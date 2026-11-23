package pe.granja.porcina.ui

import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.granja.porcina.*
import pe.granja.porcina.data.*

@Composable
fun OutlaysPage(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit,onDetail:(String,Long)->Unit){
    val context=LocalContext.current
    var title by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var project by remember{mutableStateOf("")}
    var purpose by remember{mutableStateOf("INFRA")};var payer by remember{mutableStateOf("")};var funding by remember{mutableStateOf("SOCIO")}
    var foodKg by remember{mutableStateOf("")};var lot by remember{mutableStateOf("")};var animal by remember{mutableStateOf("")}
    var receipt by remember{mutableStateOf<String?>(null)}
    var askExhaust by remember{mutableStateOf(false)};var showOld by remember{mutableStateOf(false)}
    var previousFoodIds by remember{mutableStateOf<Set<Long>>(emptySet())}
    var oldFood by remember{mutableStateOf("")};var consumerLot by remember{mutableStateOf("")}
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){
            runCatching {
                val folder=File(context.filesDir,"receipts").also{it.mkdirs()}
                val suffix=if(context.contentResolver.getType(uri)=="application/pdf")"pdf" else "jpg"
                val target=File(folder,"comprobante_${System.currentTimeMillis()}.$suffix")
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use{output->input.copyTo(output)} }
                    ?:error("No se pudo copiar el comprobante")
                receipt=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",target).toString()
            }
        }
    }
    ScrollPage("Gastos y compras"){
        Section("Registrar movimiento"){
            Field("Qué compraste o pagaste",title,{title=it})
            Field("Importe S/",amount,{amount=it})
            Choice("¿Para qué es?",purpose,listOf("INFRA" to "Construcción de corrales / infraestructura",
                "ALIMENTO" to "Alimento para animales","SANIDAD" to "Vacunas o medicamentos",
                "OPERATIVO" to "Gasto operativo asignable","OTRO" to "Otro (revisión financiera)")){purpose=it}
            Field("Proyecto o destino (p. ej. Corrales)",project,{project=it})
            Choice("Origen de fondos",funding,listOf("SOCIO" to "Pago personal de socio","CAJA" to "Caja del negocio")){funding=it}
            if(funding=="SOCIO") Choice("Pagado por",payer,s.partners.map{it.id.toString() to it.name}){payer=it}
            if(purpose=="ALIMENTO") {Field("Cantidad total de alimento (kg)",foodKg,{foodKg=it})
                Text("La compra entra al almacén. El costo se asigna al consumirse.",style=MaterialTheme.typography.bodySmall)}
            if(purpose in listOf("SANIDAD","OPERATIVO")) {
                Choice("Destino del costo",if(animal.isNotEmpty())"ANIMAL" else if(lot.isNotEmpty())"LOTE" else "GENERAL",
                    listOf("GENERAL" to "Todos los animales activos","LOTE" to "Lote específico","ANIMAL" to "Chancho específico")){
                        when(it){"GENERAL"->{animal="";lot=""};"LOTE"->{animal="";lot="SELECT"};"ANIMAL"->{animal="SELECT";lot=""}}
                    }
                if(lot.isNotEmpty()) Choice("Lote",lot,s.lots.map{it.id.toString() to it.name}){lot=it}
                if(animal.isNotEmpty()) Choice("Animal",animal,s.active().map{it.id.toString() to it.code}){animal=it}
            }
            OutlinedButton(onClick={pick.launch(arrayOf("image/*","application/pdf"))}){
                Icon(Icons.Default.AttachFile,null);Text(if(receipt==null)" Adjuntar boleta / comprobante" else " Comprobante seleccionado")}
            Text("Clasificación: "+when(purpose){"INFRA"->"Activo; no se descuenta completo de la primera venta";
                "ALIMENTO"->"Existencia: costeo posterior por consumo";"SANIDAD","OPERATIVO"->"Costo asignado a animales";else->"Gasto a revisar"},style=MaterialTheme.typography.bodySmall,color=Green)
            Button(modifier=Modifier.fillMaxWidth(),onClick={
                previousFoodIds=s.foods.map{it.id}.toSet()
                task{
                    repo.newOutlay(title,purpose,project,Soles.parse(amount),payer.toLongOrNull(),funding,
                        lot.toLongOrNull(),animal.toLongOrNull(),receipt,foodKg.toDoubleOrNull()?:0.0)
                    if(purpose=="ALIMENTO") {askExhaust=true;showOld=false}
                }
            }) {Icon(Icons.Default.Save,null);Text(" Guardar compra o gasto")}
        }
        Section("Historial de movimientos"){
            if(s.outlays.isEmpty()) Text("Aún no se registraron movimientos.")
            s.outlays.forEach{ e ->
                Row(Modifier.fillMaxWidth().clickable{onDetail("GASTO",e.id)},horizontalArrangement=Arrangement.SpaceBetween){
                    Column(Modifier.weight(1f)){
                        Text(e.title,fontWeight=FontWeight.SemiBold)
                        Text("${e.purpose} · Pagado por ${s.partnerName(if(e.funding=="CAJA")null else e.payerId)} · ${day(e.at)}",style=MaterialTheme.typography.bodySmall)
                        if(e.voided) Text("ANULADO",color=Color.Red)
                    };Text(Soles.show(e.cents),fontWeight=FontWeight.Bold)
                };HorizontalDivider()
            }
        }
    }
    if(askExhaust) AlertDialog(onDismissRequest={askExhaust=false},title={Text("Compra de alimento registrada")},
        text={Text("¿Quieres actualizar algún alimento anterior como agotado?")},
        confirmButton={TextButton(onClick={askExhaust=false;showOld=true}){Text("Sí, seleccionar")}},
        dismissButton={TextButton(onClick={askExhaust=false}){Text("No, finalizar")}})
    if(showOld){val foods=s.foods.filter{it.id in previousFoodIds && it.remainingKg>0 && s.outlay(it.outlayId)?.voided!=true}
        AlertDialog(onDismissRequest={showOld=false},title={Text("Marcar alimento anterior agotado")},
            text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                if(foods.isEmpty()) Text("No hay partidas anteriores disponibles.")
                else Choice("Alimento",oldFood,foods.map{it.id.toString() to "${it.title} · ${it.remainingKg} kg"}){oldFood=it}
                Choice("Lote que lo consumió",consumerLot,s.lots.map{it.id.toString() to it.name}){consumerLot=it}
                Text("Se registrará la fecha actual y se distribuirá el costo del saldo restante. Si fue desperdicio, usa Almacén → Registrar consumo.",style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton(enabled=oldFood.toLongOrNull()!=null&&foods.any{it.id.toString()==oldFood},onClick={
                val f=foods.first{it.id.toString()==oldFood}
                task{repo.useFood(f.id,f.remainingKg,consumerLot.toLongOrNull(),null,false)};showOld=false
            }){Text("Confirmar agotado")}},dismissButton={TextButton(onClick={showOld=false}){Text("Cancelar")}})
    }
}

@Composable fun PartnersPage(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit){
    var tab by remember{mutableStateOf("INVERSIONES")}
    ScrollPage("Socios y capital"){
        Choice("Ver",tab,listOf("INVERSIONES" to "Desembolsos por socio","SOCIOS" to "Registrar socios","APORTES" to "Capital / aportes")){tab=it}
        when(tab){
            "INVERSIONES"->{
                val amounts=s.partners.map{it to s.socioPaid(it.id)}
                Section("¿Cuánto desembolsó cada socio?"){
                    if(amounts.isEmpty()) Text("Primero crea tus dos socios.")
                    amounts.forEach{(p,v)->Line(p.name,Soles.show(v))}
                    if(amounts.size==2) {
                        val diff=kotlin.math.abs(amounts[0].second-amounts[1].second)
                        val behind=amounts.minByOrNull{it.second}?.first?.name?:""
                        HorizontalDivider();Line("Diferencia para igualar",Soles.show(diff))
                        Text(if(diff==0L)"Desembolsos igualados" else "$behind ha desembolsado ${Soles.show(diff)} menos. No significa necesariamente que deba ese importe personalmente.",style=MaterialTheme.typography.bodySmall)
                    }
                }
                Section("Historial pagado con dinero personal"){
                    s.validOutlays().filter{it.funding=="SOCIO"}.forEach { x ->
                        Line("${x.title} · ${s.partnerName(x.payerId)}",Soles.show(x.cents))
                    };if(s.validOutlays().none{it.funding=="SOCIO"}) Text("Sin desembolsos personales registrados.")
                }
            }
            "SOCIOS"->{
                var name by remember{mutableStateOf("")};var share by remember{mutableStateOf("50")};var link by remember{mutableStateOf("NO")}
                Section("Crear socio (máximo 2)"){
                    Field("Nombre completo",name,{name=it});Field("Participación %",share,{share=it})
                    Choice("Usuario vinculado",link,listOf("NO" to "Sin usuario")+s.users.map{it.id to "${it.name} (${it.email})"}){link=it}
                    Button(onClick={task{repo.addPartner(name,share.toIntOrNull()?:0,link.takeIf{it!="NO"})}}){Text("Registrar socio")}
                }
                Section("Socios existentes") {s.partners.forEach{ p -> Line(p.name,"${p.share}% · "+(s.users.firstOrNull{it.id==p.userId}?.email?:"Sin usuario")) }}
            }
            "APORTES"->{
                var socio by remember{mutableStateOf("")};var amount by remember{mutableStateOf("")};var kind by remember{mutableStateOf("CAPITAL")};var note by remember{mutableStateOf("")}
                Section("Registrar movimiento de capital"){
                    Choice("Socio",socio,s.partners.map{it.id.toString() to it.name}){socio=it}
                    Choice("Tipo",kind,listOf("CAPITAL" to "Aporte a caja","RETIRO" to "Devolución de capital")){kind=it}
                    Field("Monto S/",amount,{amount=it});Field("Observación",note,{note=it})
                    Button(onClick={task{repo.addContribution(socio.toLongOrNull()?:0L,Soles.parse(amount),kind,note)}}){Text("Guardar movimiento")}
                }
                Section("Resumen de aportes"){
                    Line("Capital aportado",Soles.show(s.contributions.filter{it.kind=="CAPITAL"}.sumOf{it.cents}))
                    Line("Capital devuelto",Soles.show(s.contributions.filter{it.kind=="RETIRO"}.sumOf{it.cents}))
                    s.contributions.forEach{ c -> Line("${s.partnerName(c.partnerId)} · ${c.kind}",Soles.show(c.cents)) }
                    Text("Un desembolso personal puede registrarse por separado de un aporte depositado a la caja. No lo registres dos veces.",style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
