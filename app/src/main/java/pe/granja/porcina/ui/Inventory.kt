package pe.granja.porcina.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.granja.porcina.*
import pe.granja.porcina.data.*

@Composable
fun InventoryPage(s:AgroSnapshot, repo:AgroRepository,task:(suspend ()->Unit)->Unit,onDetail:(String,Long)->Unit){
    var tab by remember{mutableStateOf("ANIMALES")}
    ScrollPage("Inventario") {
        Choice("Sección",tab,listOf("ANIMALES" to "Chanchos","LOTES" to "Lotes / compras","PARTOS" to "Partos y crías","INSUMOS" to "Almacén de alimentos")){tab=it}
        when(tab){
            "ANIMALES"->AnimalsPane(s,repo,task,onDetail)
            "LOTES"->LotsPane(s,repo,task)
            "PARTOS"->BirthsPane(s,repo,task)
            "INSUMOS"->FoodsPane(s,repo,task)
        }
    }
}

@Composable private fun AnimalsPane(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit,onDetail:(String,Long)->Unit){
    val active=s.active()
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Stat("Activos","${active.size}",Modifier.weight(1f));Stat("Hembras","${active.count{it.sex=="HEMBRA"}}",Modifier.weight(1f))
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
        Stat("Machos","${active.count{it.sex=="MACHO"}}",Modifier.weight(1f))
        Stat("Crías","${active.count{it.stage in listOf("LACTANTE","DESTETADO")}}",Modifier.weight(1f))
    }
    var search by remember{mutableStateOf("")};var sex by remember{mutableStateOf("TODOS")}
    var stage by remember{mutableStateOf("TODAS")};var minKg by remember{mutableStateOf("")};var maxKg by remember{mutableStateOf("")}
    var minMonths by remember{mutableStateOf("")};var maxMonths by remember{mutableStateOf("")}
    Section("Filtrar animales"){
        Field("Código o arete",search,{search=it})
        Choice("Sexo",sex,listOf("TODOS" to "Todos","MACHO" to "Machos","HEMBRA" to "Hembras","POR_DETERMINAR" to "Sin definir")){sex=it}
        Choice("Etapa",stage,listOf("TODAS" to "Todas","CRIAS" to "Crías (lactantes y destetadas)","LACTANTE" to "Lactante","DESTETADO" to "Destetado","CRECIMIENTO" to "Crecimiento","ENGORDE" to "Engorde","REPRODUCTOR" to "Reproductor")){stage=it}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Field("Peso desde kg",minKg,{minKg=it})};Box(Modifier.weight(1f)){Field("Hasta kg",maxKg,{maxKg=it})}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Box(Modifier.weight(1f)){Field("Edad desde meses",minMonths,{minMonths=it})};Box(Modifier.weight(1f)){Field("Hasta meses",maxMonths,{maxMonths=it})}}
        TextButton(onClick={search="";sex="TODOS";stage="TODAS";minKg="";maxKg="";minMonths="";maxMonths=""}){Text("Limpiar filtros")}
    }
    val matches=active.filter { a->
        val months=a.birthAt?.let{((System.currentTimeMillis()-it)/86400000.0/30.4375)}
        (search.isBlank()||a.code.contains(search,true)||(a.tag?.contains(search,true)==true)) &&
        (sex=="TODOS"||a.sex==sex)&& (stage=="TODAS"||(stage=="CRIAS"&&a.stage in listOf("LACTANTE","DESTETADO"))||a.stage==stage)&&
        (minKg.toDoubleOrNull()==null||a.weightKg>=minKg.toDouble()) && (maxKg.toDoubleOrNull()==null||a.weightKg<=maxKg.toDouble()) &&
        (minMonths.toDoubleOrNull()==null||(months!=null&&months>=minMonths.toDouble()))&&
        (maxMonths.toDoubleOrNull()==null||(months!=null&&months<=maxMonths.toDouble()))
    }
    Section("Animales encontrados (${matches.size})"){
        if(matches.isEmpty()) Text("Sin animales que coincidan con los filtros.",color=Color.Gray)
        matches.forEach { a ->
            Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth()){
                Column(Modifier.weight(1f).clickable{onDetail("ANIMAL",a.id)}){
                    Text("🐖 ${a.code}"+(a.tag?.let{" · Arete $it"}?:""),fontWeight=FontWeight.SemiBold)
                    Text("${a.sex.lowercase()} · ${age(a.birthAt)} · ${a.weightKg} kg · ${a.stage.lowercase()}",style=MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(onClick={onDetail("ANIMAL",a.id)}){Text("Vender / Ver")}
            };HorizontalDivider()
        }
    }
    if(s.animals.any{it.status!="ACTIVO"}) Section("Otros estados"){
        s.animals.filter{it.status!="ACTIVO"}.forEach{a->TextButton(onClick={onDetail("ANIMAL",a.id)}){
            Text("${a.code} · ${a.status}")}}
    }
}

@Composable private fun LotsPane(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit){
    var title by remember{mutableStateOf("")};var count by remember{mutableStateOf("1")}
    var amount by remember{mutableStateOf("")};var kg by remember{mutableStateOf("0")};var sex by remember{mutableStateOf("POR_DETERMINAR")}
    var funding by remember{mutableStateOf("SOCIO")};var payer by remember{mutableStateOf(s.partners.firstOrNull()?.id?.toString()?:"")}
    Section("Registrar compra por lote") {
        Field("Nombre del lote / proveedor",title,{title=it});Field("Cantidad de chanchos",count,{count=it})
        Field("Costo total de compra S/",amount,{amount=it});Field("Peso inicial por animal (kg)",kg,{kg=it})
        Choice("Sexo",sex,listOf("POR_DETERMINAR" to "Por definir","MACHO" to "Macho","HEMBRA" to "Hembra")){sex=it}
        Choice("Origen del dinero",funding,listOf("SOCIO" to "Pagó un socio","CAJA" to "Caja del negocio")){funding=it}
        if(funding=="SOCIO") Choice("Pagado por",payer,s.partners.map{it.id.toString() to it.name}){payer=it}
        Button(onClick={task{repo.newLot(title,count.toIntOrNull()?:0,
            if(amount.isBlank()) 0L else Soles.parse(amount),sex,kg.toDoubleOrNull()?:0.0,
            if(funding=="SOCIO")payer.toLongOrNull() else null,funding)}},modifier=Modifier.fillMaxWidth()){
            Icon(Icons.Default.Add,null);Spacer(Modifier.width(8.dp));Text("Crear lote y fichas individuales")}
    }
    Section("Lotes registrados"){
        if(s.lots.isEmpty()) Text("No has registrado lotes todavía.")
        s.lots.forEach{lot->Line(lot.name,"${s.animals.count{it.lotId==lot.id}} animales")}
    }
}

@Composable private fun BirthsPane(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit){
    val mothers=s.active().filter{it.sex=="HEMBRA"};val fathers=s.active().filter{it.sex=="MACHO"}
    var mother by remember{mutableStateOf("")};var father by remember{mutableStateOf("NO")}
    var total by remember{mutableStateOf("1")};var alive by remember{mutableStateOf("1")}
    Section("Registrar parto"){
        if(mothers.isEmpty()) Text("Primero registra o identifica una hembra activa.")
        else {
            Choice("Madre",mother,mothers.map{it.id.toString() to it.code}){mother=it}
            Choice("Padre (opcional)",father,listOf("NO" to "Sin identificar")+fathers.map{it.id.toString() to it.code}){father=it}
            Field("Crías nacidas",total,{total=it});Field("Crías vivas",alive,{alive=it})
            Button(onClick={task{repo.newBirth(mother.toLongOrNull()?:0L,father.toLongOrNull(),total.toIntOrNull()?:0,alive.toIntOrNull()?:0)}}){Text("Guardar y crear fichas de crías")}
        }
    }
    Section("Partos registrados") { if(s.births.isEmpty()) Text("Sin partos registrados")
        s.births.forEach{b->Text("${day(b.at)} · Madre: ${s.animals.firstOrNull{it.id==b.motherId}?.code?:b.motherId} · ${b.living}/${b.total} vivas")}}
}

@Composable private fun FoodsPane(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit){
    val available=s.foods.filter{it.remainingKg>0 && s.outlay(it.outlayId)?.voided!=true}
    Section("Almacén de alimentos"){
        Line("Partidas disponibles","${available.size}")
        if(s.foods.isEmpty()) Text("Compra alimento desde Gastos y compras para que aparezca aquí.")
        s.foods.filter{s.outlay(it.outlayId)?.voided!=true}.forEach { f->
            var show by remember(f.id){mutableStateOf(false)}
            Column{Text("${f.title} · AL-${f.id.toString().padStart(3,'0')}",fontWeight=FontWeight.SemiBold)
                Text("${f.remainingKg} de ${f.quantityKg} kg · ${if(f.remainingKg<=0)"Agotado" else "Disponible"}")
                if(f.exhaustedAt!=null) Text("Duró ${((f.exhaustedAt-(f.openedAt?:f.createdAt))/86400000).coerceAtLeast(0)+1} días (desde primer uso)",style=MaterialTheme.typography.bodySmall)
                if(f.remainingKg>0) OutlinedButton(onClick={show=true}){Text("Registrar consumo / agotado")}
            }
            if(show){ var qty by remember{mutableStateOf(f.remainingKg.toString())};var lot by remember{mutableStateOf("")};var waste by remember{mutableStateOf(false)}
                AlertDialog(onDismissRequest={show=false},title={Text("Consumir o agotar alimento")},text={Column{
                    Text("${f.title} · ${f.remainingKg} kg restantes")
                    Field("Cantidad utilizada (kg)",qty,{qty=it})
                    Choice("Lote",lot,s.lots.map{it.id.toString() to it.name}){lot=it}
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(waste,{waste=it});Text("Fue pérdida o merma")}
                }},confirmButton={TextButton(onClick={task{repo.useFood(f.id,qty.toDoubleOrNull()?:0.0,lot.toLongOrNull(),null,waste)};show=false}){Text("Confirmar")}},
                    dismissButton={TextButton(onClick={show=false}){Text("Cancelar")}})
            }
            HorizontalDivider()
        }
    }
}
