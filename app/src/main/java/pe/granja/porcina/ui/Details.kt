package pe.granja.porcina.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import pe.granja.porcina.*
import pe.granja.porcina.data.*

@Composable
fun DetailPopup(s:AgroSnapshot,kind:String,id:Long,onDismiss:()->Unit,
    onSell:(String,String,String)->Unit,onEditAnimal:(String,String,String,String,String,String)->Unit,
    onDeath:(String,String)->Unit,onVoid:(String)->Unit={}){
    val context=LocalContext.current
    val a=s.animals.firstOrNull{it.id==id}
    var mode by remember(id,kind){mutableStateOf("INFO")}
    var price by remember{mutableStateOf("")};var weight by remember(a?.id){mutableStateOf(a?.weightKg?.toString()?:"")};var buyer by remember{mutableStateOf("")}
    var sex by remember(a?.id){mutableStateOf(a?.sex?:"POR_DETERMINAR")};var stage by remember(a?.id){mutableStateOf(a?.stage?:"CRECIMIENTO")}
    var tag by remember(a?.id){mutableStateOf(a?.tag?:"")};var notes by remember(a?.id){mutableStateOf(a?.notes?:"")}
    var birth by remember(a?.id){mutableStateOf(a?.birthAt?.let{java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()}?:"")}
    var reason by remember{mutableStateOf("")}
    val sale=s.sales.firstOrNull{it.id==id}
    val outlay=s.outlays.firstOrNull{it.id==id}
    AlertDialog(onDismissRequest=onDismiss,title={Text(when(kind){"ANIMAL"->"${a?.code?:"Animal"} · Detalle";"VENTA"->"Venta #$id";"GASTO"->"Compra / gasto #$id";else->"Detalle"})},
        text={Column(Modifier.heightIn(max=550.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(9.dp)){
            when(kind){
                "ANIMAL" -> if(a!=null) {
                    Text("Estado: ${a.status} · Origen: ${a.origin}")
                    Text("Arete: ${a.tag?:"Sin asignar"} · ${a.sex.lowercase()} · ${age(a.birthAt)}")
                    Text("Peso: ${a.weightKg} kg · Etapa: ${a.stage}")
                    Choice("Acción",mode, listOf("INFO" to "Ficha / costos","VENDER" to "Vender animal","EDITAR" to "Editar ficha","MUERTE" to "Registrar fallecimiento")){mode=it}
                    when(mode){
                        "INFO"->{
                            Line("Costo de compra",Soles.show(a.buyCents))
                            Line("Alimento, salud y otros",Soles.show(s.byAnimal(a.id)))
                            Line("Costo de crianza acumulado",Soles.show(s.cost(a.id)))
                            val depreciation=AgroSnapshot.infraCost(a,s.outlays,s.animals,System.currentTimeMillis(),s.lifeMonths())
                            Line("Uso estimado de corrales",Soles.show(depreciation))
                            Line("Costo total referencial",Soles.show(s.cost(a.id)+depreciation))
                            if(a.weightKg>0) Line("Punto de equilibrio por kg",Soles.show(((s.cost(a.id)+depreciation)/a.weightKg).toLong()))
                            Text("Los corrales son una asignación estimada durante el uso; no se vuelven a pagar.",style=MaterialTheme.typography.bodySmall,color=Color.Gray)
                            if(a.notes.isNotBlank())Text("Observaciones: ${a.notes}")
                        }
                        "EDITAR"->{
                            Field("Arete único",tag,{tag=it})
                            Choice("Sexo",sex,listOf("MACHO" to "Macho","HEMBRA" to "Hembra","POR_DETERMINAR" to "Por determinar")){sex=it}
                            Choice("Etapa",stage,listOf("LACTANTE" to "Lactante","DESTETADO" to "Destetado","CRECIMIENTO" to "Crecimiento","ENGORDE" to "Engorde","REPRODUCTOR" to "Reproductor")){stage=it}
                            Field("Peso kg",weight,{weight=it});Field("Nacimiento (AAAA-MM-DD, opcional)",birth,{birth=it});Field("Observaciones",notes,{notes=it})
                            Button(enabled=a.status=="ACTIVO",onClick={onEditAnimal(tag,sex,stage,weight,birth,notes)}){Text("Guardar cambios")}
                        }
                        "VENDER"->{
                            Field("Monto total de venta S/",price,{price=it});Field("Peso final kg",weight,{weight=it});Field("Comprador",buyer,{buyer=it})
                            Text("Costo registrado + corrales: ${Soles.show(s.cost(a.id)+AgroSnapshot.infraCost(a,s.outlays,s.animals,System.currentTimeMillis(),s.lifeMonths()))}")
                            val projected=(price.toDoubleOrNull()?:0.0)*100-(s.cost(a.id)+AgroSnapshot.infraCost(a,s.outlays,s.animals,System.currentTimeMillis(),s.lifeMonths()))
                            Text("Ganancia estimada: ${Soles.show(projected.toLong())}")
                            Button(enabled=a.status=="ACTIVO",onClick={onSell(price,weight,buyer)}){Text("Confirmar venta")}
                        }
                        "MUERTE"->{
                            Text("Fecha de fallecimiento: hoy (${day(System.currentTimeMillis())})")
                            Field("Motivo o causa",reason,{reason=it});Field("Observaciones",notes,{notes=it})
                            Button(enabled=a.status=="ACTIVO",colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFAA3333)),onClick={onDeath(reason,notes)}){Text("Registrar fallecimiento")}
                        }
                    }
                } else Text("Animal no encontrado")
                "VENTA" -> if(sale!=null){
                    Text("Animal: ${s.animals.firstOrNull{it.id==sale.animalId}?.code?:"No encontrado"}")
                    Line("Precio",Soles.show(sale.cents));Line("Peso final","${sale.weightKg} kg")
                    Line("Costo del animal",Soles.show(s.cost(sale.animalId)))
                    Line("Infraestructura asignada",Soles.show(sale.infrastructureCostCents))
                    Line("Ganancia",Soles.show(sale.cents-s.cost(sale.animalId)-sale.infrastructureCostCents))
                    Text("Comprador: ${sale.buyer} · ${day(sale.at)}")
                    Text("Registrado por: ${s.users.firstOrNull{it.id==sale.actor}?.name?:sale.actor}")
                }else Text("Venta no encontrada")
                "GASTO" -> if(outlay!=null){
                    Line("Concepto",outlay.title);Line("Importe",Soles.show(outlay.cents));Line("Destino",outlay.purpose)
                    Line("Proyecto",outlay.project);Line("Pagado por",s.partnerName(if(outlay.funding=="CAJA")null else outlay.payerId))
                    Line("Registrado por",s.users.firstOrNull{it.id==outlay.actor}?.name?:outlay.actor)
                    if(outlay.receiptUri!=null) OutlinedButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(outlay.receiptUri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))}}){Text("Ver comprobante")}
                    Text(if(outlay.voided)"ANULADO: ${outlay.voidReason}" else "Activo")
                    if(!outlay.voided && s.user.role==Roles.OWNER){Field("Motivo para anular",reason,{reason=it});Button(onClick={onVoid(reason)}){Text("Anular movimiento")}}
                }else Text("Gasto no encontrado")
                else -> Text("Consulta el movimiento desde su módulo correspondiente.")
            }
        }},confirmButton={TextButton(onClick=onDismiss){Text("Cerrar")}})
}
