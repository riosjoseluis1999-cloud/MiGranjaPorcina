package pe.granja.porcina.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.math.max
import kotlin.math.min

object Soles {
    fun parse(v:String):Long = BigDecimal(v.trim().replace(',','.')).setScale(2,RoundingMode.HALF_UP).also {
        require(it > BigDecimal.ZERO) { "El importe debe ser mayor que cero" }
    }.movePointRight(2).longValueExact()
    fun show(v:Long):String = "S/ " + BigDecimal.valueOf(v,2).setScale(2).toPlainString()
}
object Crypto {
    fun salt():String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
    fun hash(password:String, salt:String):String {
        val spec=PBEKeySpec(password.toCharArray(),Base64.getDecoder().decode(salt),120_000,256)
        return try { Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded) }
        finally { spec.clearPassword() }
    }
    fun ok(password:String,salt:String,expectedHash:String)=MessageDigest.isEqual(
        Base64.getDecoder().decode(hash(password,salt)),Base64.getDecoder().decode(expectedHash))
}

class AgroRepository(private val db:AgroDb) {
    private val d=db.dao()
    var current:User?=null
        private set
    private fun actor()=current?.id ?: error("Inicia sesión")
    private suspend fun checkPerm(module:String,op:String) {
        val u=current ?: error("Inicia sesión")
        check(d.userById(u.id)?.enabled==true) { "Tu cuenta fue bloqueada" }
        if(u.role!=Roles.OWNER) {
            check(d.flags().none { it.name=="MAINTENANCE" && it.value }) { "Sistema en mantenimiento" }
            val p=d.permission(u.role,module) ?: error("Sin permisos")
            check(when(op){"view"->p.view;"create"->p.create;"edit"->p.edit;"void"->p.void;else->false}) {"Operación no permitida"}
        }
    }
    suspend fun can(module:String, op:String):Boolean=runCatching { checkPerm(module,op);true }.getOrDefault(false)
    private suspend fun log(module:String,action:String,text:String,entityId:Long?=null,notify:Boolean=false) {
        val u=actor(); val now=System.currentTimeMillis()
        d.addAudit(Audit(actor=u,module=module,action=action,details=text,at=now))
        if(notify) {
            val displayName=d.userById(u)?.name ?: u
            d.addNotice(Notice(text="$displayName · $action: $text",module=module,entityId=entityId,at=now))
        }
    }
    suspend fun initialize()=db.withTransaction {
        if(d.users().isNotEmpty()) return@withTransaction
        val tests=listOf(Triple("U001","Administrador principal","admin@granja.local"),Triple("U002","Administrador operativo","socio@granja.local"),Triple("U003","Visualizador","visor@granja.local"))
        val passes=listOf("Admin1234!","Socio1234!","Visor1234!")
        val roles=listOf(Roles.OWNER,Roles.OPERATOR,Roles.VIEWER)
        tests.forEachIndexed { i,t -> val salt=Crypto.salt();d.putUser(User(t.first,t.second,t.third,salt,Crypto.hash(passes[i],salt),roles[i])) }
        Mods.list.forEach { (id,_) ->
            d.putPermission(RolePerm(Roles.OWNER,id,true,true,true,true))
            val allowed=id!=Mods.ADMIN
            d.putPermission(RolePerm(Roles.OPERATOR,id,allowed,allowed&&id!=Mods.PARTNERS,allowed&&id!=Mods.PARTNERS,false))
            val view=id in listOf(Mods.HOME,Mods.INVENTORY,Mods.REPORTS)
            d.putPermission(RolePerm(Roles.VIEWER,id,view,false,false,false))
        }
        d.putFlag(Flag("ALLOW_LOGIN",true));d.putFlag(Flag("MAINTENANCE",false))
        d.putConfig(ConfigValue("INFRA_LIFE_MONTHS","36"))
        // Ningún animal, socio, gasto, aporte ni venta ficticia se inserta en Room.
    }
    suspend fun login(email:String,password:String):String {
        val u=d.userByEmail(email.trim().lowercase()) ?: return "Usuario o contraseña incorrectos"
        if(!Crypto.ok(password,u.salt,u.hash)) return "Usuario o contraseña incorrectos"
        if(!u.enabled) return "Tu usuario está bloqueado"
        val flags=d.flags()
        if(u.role!=Roles.OWNER && (flags.any { it.name=="MAINTENANCE"&&it.value } || flags.any { it.name=="ALLOW_LOGIN"&&!it.value })) return "Acceso temporalmente suspendido"
        current=u
        log(Mods.ADMIN,"LOGIN","Acceso de ${u.name}")
        return ""
    }
    suspend fun logout(){if(current!=null) log(Mods.ADMIN,"LOGOUT","Sesión cerrada");current=null}
    suspend fun finishTour() { val u=current?:return;d.putUser(u.copy(onboardingCompleted=true));current=u.copy(onboardingCompleted=true) }
    suspend fun refresh():AgroSnapshot {
        checkPerm(Mods.HOME,"view")
        val u=d.userById(actor())?:error("Usuario no encontrado")
        current=u
        val modules=Mods.list.filter { can(it.first,"view") }.map { it.first }.toSet()
        // La autorización se comprueba también en cada método de escritura.
        return AgroSnapshot(u,modules,d.users(),d.perms(),d.flags(),d.configs(),d.partners(),d.contributions(),d.lots(),
            d.animals(),d.births(),d.outlays(),d.foods(),d.usages(),d.allocations(),d.sales(),if(Mods.ADMIN in modules)d.audits() else emptyList(),d.notices().filter{it.module in modules})
    }
    suspend fun addPartner(name:String,share:Int,userId:String?)=db.withTransaction {
        checkPerm(Mods.PARTNERS,"create")
        require(name.isNotBlank()&&share in 1..100){"Datos de socio inválidos"}
        require(d.partners().size<2){"Esta versión admite dos socios"}
        if(userId!=null) require(d.partners().none{it.userId==userId}){"Usuario ya vinculado a otro socio"}
        val id=d.addPartner(Partner(name=name.trim(),share=share,userId=userId))
        log(Mods.PARTNERS,"SOCIO","Nuevo socio: $name ($share%)",id,true)
    }
    suspend fun addContribution(partnerId:Long,cents:Long,kind:String,note:String)=db.withTransaction {
        checkPerm(Mods.PARTNERS,"create")
        require(cents>0 && d.partners().any{it.id==partnerId}){"Aporte inválido"}
        val id=d.addContribution(Contribution(partnerId=partnerId,cents=cents,kind=kind,note=note,actor=actor()))
        log(Mods.PARTNERS,"CAPITAL","${d.partners().first{it.id==partnerId}.name}: ${Soles.show(cents)} ($kind)",id,true)
    }
    suspend fun newLot(title:String,count:Int,cents:Long,sex:String,weight:Double,payerId:Long?,funding:String)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"create")
        require(title.isNotBlank() && count in 1..200 && cents>=0 && weight>=0) {"Datos de compra incorrectos"}
        require(funding=="CAJA" || payerId!=null && d.partners().any{it.id==payerId}) {"Selecciona el socio que pagó"}
        val now=System.currentTimeMillis()
        val lot=d.addLot(Lot(name=title,at=now,actor=actor()))
        val initial=cents/count;val rem=cents%count
        repeat(count){ i -> val id=d.addAnimal(Animal(lotId=lot,sex=sex,weightKg=weight,buyCents=initial+(if(i<rem)1 else 0),actor=actor()))
            // Arete físico no se asigna automáticamente: se valida cuando se coloca.
            d.animal(id)?.let{d.updateAnimal(it.copy(code="CH-%03d".format(id)))}
        }
        if(cents>0) d.addOutlay(Outlay(title="Compra de $count chanchos · $title",purpose="ANIMALES",category="Compra de animales",
            project=title,cents=cents,payerId=payerId,funding=funding,lotId=lot,at=now,actor=actor()))
        log(Mods.INVENTORY,"COMPRA","Lote $title: $count animales; ${Soles.show(cents)}",lot,true)
    }
    suspend fun newBirth(motherId:Long,fatherId:Long?,count:Int,alive:Int)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"create")
        val m=d.animal(motherId)?:error("Madre no encontrada")
        require(m.status=="ACTIVO"&&m.sex=="HEMBRA"){"Selecciona una hembra activa"}
        require(count in 1..30 && alive in 0..count){"Cantidad inválida"}
        val now=System.currentTimeMillis()
        d.addBirth(Birth(motherId=motherId,fatherId=fatherId,total=count,living=alive,at=now,actor=actor()))
        repeat(alive){val id=d.addAnimal(Animal(lotId=m.lotId,origin="NACIDO",stage="LACTANTE",birthAt=now,
            acquiredAt=now,motherId=motherId,fatherId=fatherId,actor=actor()))
            d.animal(id)?.let{d.updateAnimal(it.copy(code="CH-%03d".format(id)))} }
        log(Mods.INVENTORY,"PARTO","${m.code}: nacidos $count, vivos $alive",motherId,true)
    }
    suspend fun updateAnimal(id:Long,tag:String,sex:String,stage:String,weight:Double,birthAt:Long?,notes:String)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"edit")
        val a=d.animal(id)?:error("Animal inexistente")
        val nextTag=tag.trim().ifEmpty{null}
        if(nextTag!=a.tag && nextTag!=null){
            require(d.tags().none{it.tag==nextTag}){"El arete ya está o estuvo asignado"}
            d.addTag(TagHistory(animalId=id,tag=nextTag,at=System.currentTimeMillis()))
        }
        require(weight>=0) {"Peso inválido"}
        d.updateAnimal(a.copy(tag=nextTag,sex=sex,stage=stage,weightKg=weight,birthAt=birthAt,notes=notes))
        log(Mods.INVENTORY,"EDICIÓN","${a.code}: sexo=$sex, etapa=$stage, arete=$nextTag",id,true)
    }
    suspend fun death(id:Long,at:Long,reason:String,notes:String)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"edit")
        val a=d.animal(id)?:error("Animal no encontrado")
        check(a.status=="ACTIVO"){"Animal ya no está activo"}
        require(reason.isNotBlank()){"Indica un motivo"}
        d.updateAnimal(a.copy(status="FALLECIDO",deathAt=at,deathReason=reason,notes=(a.notes+"\n"+notes).trim()))
        log(Mods.INVENTORY,"FALLECIMIENTO","${a.code}: $reason. $notes",id,true)
    }
    private suspend fun assign(type:String,id:Long,cents:Long,animalId:Long?,lotId:Long?) {
        val eligible=when {
            animalId!=null->listOfNotNull(d.animal(animalId)).filter{it.status=="ACTIVO"}
            lotId!=null->d.activeLot(lotId)
            else->d.activeAnimals()
        }.sortedBy{it.id}
        require(eligible.isNotEmpty()) {"No hay animales activos para asignar el costo"}
        val each=cents/eligible.size;val rem=cents%eligible.size
        d.putAllocations(eligible.mapIndexed{i,a->CostAllocation(type,id,a.id,each+(if(i<rem)1 else 0))})
    }
    suspend fun newOutlay(title:String,purpose:String,project:String,cents:Long,payerId:Long?,funding:String,
        lotId:Long?,animalId:Long?,receiptUri:String?,foodKg:Double=0.0)=db.withTransaction {
        checkPerm(Mods.EXPENSES,"create")
        require(title.isNotBlank()&&cents>0) {"Concepto e importe obligatorios"}
        require(funding=="CAJA" || (funding=="SOCIO"&&payerId!=null&&d.partners().any{it.id==payerId})) {"Selecciona quién pagó"}
        require(purpose in listOf("INFRA","ALIMENTO","SANIDAD","OPERATIVO","OTRO")) {"Destino inválido"}
        if(purpose=="ALIMENTO") require(foodKg>0) {"Indica la cantidad de alimento en kg"}
        val now=System.currentTimeMillis()
        val id=d.addOutlay(Outlay(title=title,purpose=purpose,category=purpose,project=project,cents=cents,
            payerId=payerId,funding=funding,animalId=animalId,lotId=lotId,receiptUri=receiptUri,at=now,actor=actor()))
        if(purpose=="ALIMENTO") d.addFood(FoodBatch(outlayId=id,title=title,quantityKg=foodKg,remainingKg=foodKg,createdAt=now))
        if(purpose=="SANIDAD" || purpose=="OPERATIVO") assign("GASTO",id,cents,animalId,lotId)
        // INFRA es un activo; ALIMENTO es existencias. No afectan los animales hasta su uso.
        log(Mods.EXPENSES,"COMPRA","$title: ${Soles.show(cents)}, destino=$purpose, pagó=${payerId?:"Caja"}",id,true)
    }
    suspend fun useFood(foodId:Long,qty:Double,lotId:Long?,animalId:Long?,waste:Boolean)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"edit")
        val f=d.food(foodId)?:error("Alimento no encontrado")
        require(lotId!=null||animalId!=null) {"Indica el lote o animal destinatario"}
        check(f.remainingKg>0) {"Ya figura agotado"}
        require(qty>0 && qty<=f.remainingKg+0.0001) {"Cantidad inválida"}
        val purchase=d.outlays().first{it.id==f.outlayId}
        check(!purchase.voided) {"Esta compra fue anulada"}
        val oldUsed=d.usages().filter{it.foodId==foodId}.sumOf{it.cents}
        val isLast=qty>=f.remainingKg-0.0001
        val newCents=if(isLast) purchase.cents-oldUsed else (purchase.cents*qty/f.quantityKg).toLong()
        val now=System.currentTimeMillis()
        val usageId=d.addUsage(FoodUsage(foodId=foodId,qtyKg=qty,lotId=lotId,animalId=animalId,waste=waste,cents=newCents,at=now,actor=actor()))
        if(!waste) assign("ALIMENTO",usageId,newCents,animalId,lotId)
        d.updateFood(f.copy(remainingKg=if(isLast)0.0 else max(0.0,f.remainingKg-qty),
            openedAt=f.openedAt?:now,exhaustedAt=if(isLast)now else null))
        log(Mods.INVENTORY,if(isLast)"AGOTADO" else "CONSUMO","${f.title}: $qty kg, ${Soles.show(newCents)}, merma=$waste",foodId,true)
    }
    suspend fun voidOutlay(id:Long,reason:String)=db.withTransaction {
        checkPerm(Mods.EXPENSES,"void")
        require(reason.length>=4) {"Explica la anulación"}
        val a=d.outlays().firstOrNull{it.id==id}?:error("Gasto inexistente")
        check(d.foods().none{it.outlayId==id && it.remainingKg<it.quantityKg}) {"No puede anularse una compra con alimento ya consumido"}
        d.updateOutlay(a.copy(voided=true,voidReason=reason))
        log(Mods.EXPENSES,"ANULACIÓN","Gasto #$id: $reason",id,true)
    }
    suspend fun sell(animalId:Long,cents:Long,kg:Double,buyer:String)=db.withTransaction {
        checkPerm(Mods.INVENTORY,"create")
        require(cents>0&&kg>0) {"Venta inválida"}
        val a=d.animal(animalId)?:error("Animal inexistente")
        check(a.status=="ACTIVO") {"Animal no disponible"}
        val now=System.currentTimeMillis()
        val life=d.configs().firstOrNull{it.key=="INFRA_LIFE_MONTHS"}?.value?.toIntOrNull()?:36
        val infra=AgroSnapshot.infraCost(a,d.outlays(),d.animals(),now,life)
        val id=d.addSale(Sale(animalId=animalId,cents=cents,weightKg=kg,buyer=buyer,
            infrastructureCostCents=infra,at=now,actor=actor()))
        d.updateAnimal(a.copy(status="VENDIDO",weightKg=kg))
        log(Mods.INVENTORY,"VENTA","${a.code}: ${Soles.show(cents)}${if(buyer.isNotBlank())", $buyer" else ""}",id,true)
    }
    suspend fun readNotice(id:Long) { d.notice(id)?.let{d.updateNotice(it.copy(read=true))} }
    suspend fun createUser(name:String,email:String,password:String,role:String)=db.withTransaction {
        checkPerm(Mods.ADMIN,"create")
        require(current?.role==Roles.OWNER){"Solo administrador principal"}
        require(name.isNotBlank() && email.contains('@') && password.length>=8) {"Nombre, correo y contraseña de 8 caracteres como mínimo"}
        require(d.userByEmail(email.lowercase())==null){"Correo registrado"}
        val salt=Crypto.salt();val id="U%03d".format(d.users().size+1)
        d.putUser(User(id,name,email.lowercase(),salt,Crypto.hash(password,salt),role))
        log(Mods.ADMIN,"USUARIO","Creó $name, rol $role")
    }
    suspend fun blockUser(id:String,enable:Boolean)=db.withTransaction {
        checkPerm(Mods.ADMIN,"edit");require(current?.role==Roles.OWNER && id!="U001"){"No puedes bloquear al principal"}
        val u=d.userById(id)?:error("No existe")
        d.putUser(u.copy(enabled=enable));log(Mods.ADMIN,"BLOQUEO","Usuario $id habilitado=$enable",notify=true)
    }
    suspend fun permission(role:String,module:String,op:String,value:Boolean)=db.withTransaction {
        checkPerm(Mods.ADMIN,"edit");require(current?.role==Roles.OWNER && role!=Roles.OWNER){"Solo el administrador principal"}
        val p=d.permission(role,module) ?:error("Permiso inexistente")
        val updated=when(op){"view"->p.copy(view=value,create=if(value)p.create else false,edit=if(value)p.edit else false,void=if(value)p.void else false)
            "create"->p.copy(create=value,view=p.view||value);"edit"->p.copy(edit=value,view=p.view||value)
            "void"->p.copy(void=value,view=p.view||value);else->error("Operación inválida")}
        d.putPermission(updated);log(Mods.ADMIN,"PERMISO","$role $module $op=$value",notify=true)
    }
    suspend fun setInfraLife(months:Int)=db.withTransaction {
        checkPerm(Mods.ADMIN,"edit");require(current?.role==Roles.OWNER){"Solo administrador principal"}
        require(months in 12..360) {"Vida útil entre 12 y 360 meses"}
        d.putConfig(ConfigValue("INFRA_LIFE_MONTHS",months.toString()))
        log(Mods.ADMIN,"CONFIGURACIÓN","Vida útil de corrales: $months meses",notify=true)
    }
    suspend fun setFlag(name:String,value:Boolean)=db.withTransaction {
        checkPerm(Mods.ADMIN,"edit");require(current?.role==Roles.OWNER){"Solo admin principal"}
        require(name=="ALLOW_LOGIN"||name=="MAINTENANCE"){"No puede apagarse auditoría"}
        d.putFlag(Flag(name,value));log(Mods.ADMIN,"CONFIGURACIÓN","$name=$value",notify=true)
    }
}

data class AgroSnapshot(val user:User,val modules:Set<String>,val users:List<User>,val permissions:List<RolePerm>,val flags:List<Flag>,
    val configs:List<ConfigValue>,val partners:List<Partner>,val contributions:List<Contribution>,val lots:List<Lot>,val animals:List<Animal>,
    val births:List<Birth>,val outlays:List<Outlay>,val foods:List<FoodBatch>,val usages:List<FoodUsage>,
    val allocations:List<CostAllocation>,val sales:List<Sale>,val audits:List<Audit>,val notices:List<Notice>) {
    fun lifeMonths():Int=configs.firstOrNull{it.key=="INFRA_LIFE_MONTHS"}?.value?.toIntOrNull()?:36
    fun active()=animals.filter{it.status=="ACTIVO"}
    fun validOutlays()=outlays.filterNot{it.voided}
    fun outlay(id:Long)=outlays.firstOrNull{it.id==id}
    fun partnerName(id:Long?)=partners.firstOrNull{it.id==id}?.name?:"Caja del negocio"
    fun byAnimal(aId:Long)=allocations.filter { it.animalId==aId && (it.sourceType!="GASTO" || validOutlays().any{x->x.id==it.sourceId}) }.sumOf{it.cents}
    fun cost(aId:Long):Long=(animals.firstOrNull{it.id==aId}?.buyCents?:0)+byAnimal(aId)
    fun infrastructureForSold(sale:Sale):Long=sale.infrastructureCostCents
    fun salesTotal()=sales.filterNot{it.voided}.sumOf{it.cents}
    fun soldCost()=sales.filterNot{it.voided}.sumOf{cost(it.animalId)+it.infrastructureCostCents}
    fun margin():Double=if(salesTotal()>0) (salesTotal()-soldCost()).toDouble()/salesTotal() *100 else 0.0
    fun socioPaid(id:Long):Long=validOutlays().filter{it.funding=="SOCIO"&&it.payerId==id}.sumOf{it.cents}
    fun invested():Long=validOutlays().sumOf{it.cents}
    fun salesProfit():Long=salesTotal()-soldCost()
    fun foodWaste():Long=usages.filter{it.waste}.sumOf{it.cents}
    fun deathLoss():Long=animals.filter{it.status=="FALLECIDO"}.sumOf{a->
        cost(a.id)+infraCost(a,outlays,animals,a.deathAt?:System.currentTimeMillis(),lifeMonths())
    }
    fun unassignedOther():Long=validOutlays().filter{it.purpose=="OTRO"}.sumOf{it.cents}
    fun resultAfterOtherKnownLosses():Long=salesProfit()-deathLoss()-foodWaste()-unassignedOther()
    fun cashRemaining():Long=contributions.filter{it.kind=="CAPITAL"}.sumOf{it.cents}+salesTotal()-validOutlays().filter{it.funding=="CAJA"}.sumOf{it.cents}-contributions.filter{it.kind=="RETIRO"}.sumOf{it.cents}
    companion object {
        // Prorrateo SIMPLE para simulación: 36 meses de uso y participantes históricos.
        // No constituye depreciación contable/fiscal certificada.
        fun infraCost(a:Animal,outlays:List<Outlay>,animals:List<Animal>,asOf:Long,lifeMonths:Int=36):Long {
            val start=max(a.acquiredAt,a.birthAt?:a.acquiredAt)
            val occupants=max(1,animals.count{it.lotId==a.lotId})
            return outlays.filter{!it.voided&&it.purpose=="INFRA"&&it.at<=asOf}.sumOf { x ->
                val occupiedDays=max(0.0,(asOf-max(start,x.at))/86400000.0)
                min(occupiedDays,lifeMonths*30.4375).let{(x.cents*(it/(lifeMonths*30.4375))/occupants).toLong()}
            }
        }
    }
}
