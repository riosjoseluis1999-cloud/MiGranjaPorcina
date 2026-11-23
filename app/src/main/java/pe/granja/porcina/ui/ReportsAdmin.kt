package pe.granja.porcina.ui

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.granja.porcina.*
import pe.granja.porcina.data.*
import java.util.Locale

@Composable fun HomePage(s:AgroSnapshot,onNavigate:(String)->Unit,onDetail:(String,Long)->Unit){
    ScrollPage("Resumen de la granja"){
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.weight(1f).clickable{onNavigate(Mods.INVENTORY)}) {Stat("Chanchos activos","${s.active().size}",Modifier.fillMaxWidth())}
            Box(Modifier.weight(1f).clickable{onNavigate(Mods.REPORTS)}) {Stat("Ventas totales",Soles.show(s.salesTotal()),Modifier.fillMaxWidth())}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.weight(1f).clickable{onNavigate(Mods.PARTNERS)}){Stat("Capital aportado",Soles.show(s.contributions.filter{it.kind=="CAPITAL"}.sumOf{it.cents}),Modifier.fillMaxWidth())}
            Box(Modifier.weight(1f).clickable{onNavigate(Mods.EXPENSES)}){Stat("Desembolsado",Soles.show(s.invested()),Modifier.fillMaxWidth())}
        }
        Section("Balance de ventas") {
            Line("Ventas de animales",Soles.show(s.salesTotal()))
            Line("Costos de animales vendidos",Soles.show(s.soldCost()))
            HorizontalDivider()
            Line("Ganancia de ventas",Soles.show(s.salesProfit()))
            val sales=s.salesTotal();val pct=if(sales>0) (s.soldCost().toFloat()/sales).coerceIn(0f,1f) else 0f
            Text("Margen: %.1f%%".format(Locale.US,s.margin()),fontWeight=FontWeight.SemiBold,color=if(s.salesProfit()<0)Color.Red else Green)
            LinearProgressIndicator(progress={pct},modifier=Modifier.fillMaxWidth().height(12.dp),color=Orange,trackColor=Color(0xFFBEE5C8))
            Text("Naranja: costos de venta · Verde: margen restante (si es positivo)",style=MaterialTheme.typography.labelSmall)
            if(sales==0L) Text("Aún no hay ventas. Los indicadores se calcularán con tus registros reales.",style=MaterialTheme.typography.bodySmall)
            Text("La ganancia de ventas no incluye gastos generales sin asignar ni impuestos.",style=MaterialTheme.typography.labelSmall,color=Color.Gray)
        }
        Section("Situación de crianza") {
            Line("Hembras activas","${s.active().count{it.sex=="HEMBRA"}}")
            Line("Machos activos","${s.active().count{it.sex=="MACHO"}}")
            Line("Crías (lactantes/destetadas)","${s.active().count{it.stage=="LACTANTE"||it.stage=="DESTETADO"}}")
            Line("Costo de animales aún activos",Soles.show(s.active().sumOf{s.cost(it.id)}))
            TextButton(onClick={onNavigate(Mods.INVENTORY)}) {Text("Ver inventario")}
        }
        Section("Actividad reciente"){
            if(s.notices.isEmpty()) Text("Sin movimientos todavía.")
            s.notices.take(4).forEach { n ->
                TextButton(onClick={n.entityId?.let{onDetail(if(n.module==Mods.EXPENSES)"GASTO" else if(n.text.contains("VENTA"))"VENTA" else "MOVIMIENTO",it)}?:onNavigate(n.module)}){
                    Text("• ${n.text}",maxLines=2)}
            }
        }
    }
}

private fun makeReportPdf(context:android.content.Context,uri:Uri,s:AgroSnapshot,type:String){
    val lines=mutableListOf("Gestión Porcina  |  Reporte: $type", "Generado: ${day(System.currentTimeMillis())}", "Usuario: ${s.user.name}", "")
    when(type){
        "Balance"->lines.addAll(listOf("Ventas: ${Soles.show(s.salesTotal())}","Costos de vendidos: ${Soles.show(s.soldCost())}",
            "Ganancia de ventas: ${Soles.show(s.salesProfit())}","Pérdidas por fallecimiento: ${Soles.show(s.deathLoss())}","Merma de alimento: ${Soles.show(s.foodWaste())}","Margen: %.1f%%".format(Locale.US,s.margin()),
            "Animales activos: ${s.active().size}","Desembolsos: ${Soles.show(s.invested())}"))
        "Socios"->s.partners.forEach { p->lines.add("${p.name}: pagó ${Soles.show(s.socioPaid(p.id))}; participación ${p.share}%") }
        "Animales"->s.animals.forEach { a->lines.add("${a.code} | ${a.status} | ${a.sex} | ${a.weightKg}kg | costo ${Soles.show(s.cost(a.id))}") }
        "Ventas"->s.sales.forEach { sale->lines.add("${day(sale.at)} | ${s.animals.firstOrNull{it.id==sale.animalId}?.code?:sale.animalId} | ${Soles.show(sale.cents)}") }
        "Gastos"->s.validOutlays().forEach { e->lines.add("${day(e.at)} | ${e.title.take(28)} | ${Soles.show(e.cents)} | ${s.partnerName(e.payerId)}") }
        "Almacén"->s.foods.forEach { f->lines.add("${f.title.take(32)} | inicial ${f.quantityKg} kg | saldo ${f.remainingKg} kg") }
    }
    val pdf=PdfDocument()
    val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    val pages=lines.chunked(34).ifEmpty{listOf(emptyList())}
    pages.forEachIndexed { ix,linesOnPage ->
        val page=pdf.startPage(PdfDocument.PageInfo.Builder(595,842,ix+1).create())
        paint.color=android.graphics.Color.rgb(24,105,63);paint.textSize=18f;paint.isFakeBoldText=true
        page.canvas.drawText("TIRIOS AGRO",42f,52f,paint)
        paint.color=android.graphics.Color.BLACK;paint.textSize=12f;paint.isFakeBoldText=false
        var y=87f
        linesOnPage.forEach { line->page.canvas.drawText(line.take(86),42f,y,paint);y+=20f }
        paint.color=android.graphics.Color.DKGRAY;paint.textSize=9f
        page.canvas.drawText("Local · $type · Página ${ix+1}/${pages.size}",42f,816f,paint)
        pdf.finishPage(page)
    }
    try {context.contentResolver.openOutputStream(uri)?.use{pdf.writeTo(it)}?:error("No se pudo escribir el PDF")}
    finally {pdf.close()}
}

@Composable fun ReportsPage(s:AgroSnapshot,onDetail:(String,Long)->Unit){
    val ctx=LocalContext.current
    var type by remember{mutableStateOf("Balance")}
    var notice by remember{mutableStateOf("")}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){ uri ->
        if(uri!=null) runCatching{makeReportPdf(ctx,uri,s,type)}.onSuccess{notice="PDF guardado correctamente"}.onFailure{notice=it.message?:"Error al generar PDF"}
    }
    ScrollPage("Reportes"){
        Section("Rentabilidad de ventas"){
            Line("Ingresos",Soles.show(s.salesTotal()))
            Line("Costos asociados a vendidos",Soles.show(s.soldCost()))
            Line("Ganancia por ventas",Soles.show(s.salesProfit()))
            Line("Pérdidas por fallecimientos",Soles.show(s.deathLoss()))
            Line("Mermas de alimentos",Soles.show(s.foodWaste()))
            Line("Otros no asignados",Soles.show(s.unassignedOther()))
            Line("Resultado tras pérdidas identificadas",Soles.show(s.resultAfterOtherKnownLosses()))
            Text("Estimación sin impuestos ni gastos no registrados; las pérdidas de animales sin vender se muestran aparte del margen de ventas.",style=MaterialTheme.typography.bodySmall)
            Line("Margen","%.1f%%".format(Locale.US,s.margin()))
        }
        Section("Seguimiento de desembolsos y recuperación"){
            val invested=s.invested();val recovered=s.salesTotal()
            Line("Desembolsos registrados",Soles.show(invested))
            Line("Cobros por ventas",Soles.show(recovered))
            Line("Saldo para cubrir desembolsos",Soles.show((invested-recovered).coerceAtLeast(0)))
            Text("Indicador simple de caja: compara ingresos brutos con compras acumuladas. No representa utilidad contable ni capital ya devuelto a socios.",style=MaterialTheme.typography.bodySmall)
            Line("Capital devuelto",Soles.show(s.contributions.filter{it.kind=="RETIRO"}.sumOf{it.cents}))
        }
        Section("Ventas anteriores"){
            if(s.sales.isEmpty()) Text("Sin ventas registradas.")
            s.sales.forEach{v->TextButton(onClick={onDetail("VENTA",v.id)}){
                Text("${day(v.at)} · ${s.animals.firstOrNull{it.id==v.animalId}?.code} · ${Soles.show(v.cents)}")}}
        }
        Section("Exportar reporte") {
            Choice("Tipo de PDF",type,listOf("Balance","Socios","Animales","Ventas","Gastos","Almacén").map{it to it}){type=it}
            Button(modifier=Modifier.fillMaxWidth(),onClick={export.launch("TIRIOS_AGRO_${type}_${System.currentTimeMillis()}.pdf")}){
                Icon(Icons.Default.PictureAsPdf,null);Text(" Descargar PDF")}
            if(notice.isNotEmpty())Text(notice)
        }
    }
}

@Composable fun AdminPage(s:AgroSnapshot,repo:AgroRepository,task:(suspend ()->Unit)->Unit){
    var tab by remember {mutableStateOf("USUARIOS")}
    ScrollPage("Administración"){
        Choice("Sección",tab,listOf("USUARIOS" to "Usuarios y roles","PERMISOS" to "Permisos por módulo","FLAGS" to "Acceso y configuración","AUDITORIA" to "Auditoría")){tab=it}
        when(tab){
            "USUARIOS"->{
                var name by remember{mutableStateOf("")};var email by remember{mutableStateOf("")};var pass by remember{mutableStateOf("")}
                var role by remember{mutableStateOf(Roles.VIEWER)}
                Section("Crear usuario"){
                    Field("Nombre",name,{name=it});Field("Correo",email,{email=it});Field("Contraseña inicial",pass,{pass=it})
                    Choice("Rol",role,listOf(Roles.OPERATOR to "Administrador operativo",Roles.VIEWER to "Visualizador")){role=it}
                    Button(onClick={task{repo.createUser(name,email,pass,role)}}){Text("Crear usuario")}
                }
                Section("Usuarios registrados") {s.users.forEach{u->
                    Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){
                        Text(u.name);Text("${u.email} · ${Roles.label(u.role)}",style=MaterialTheme.typography.bodySmall)}
                        if(u.id!="U001")Switch(checked=u.enabled,onCheckedChange={task{repo.blockUser(u.id,it)}})
                    };HorizontalDivider()
                }}
            }
            "PERMISOS"->{
                var role by remember{mutableStateOf(Roles.OPERATOR)}
                Choice("Rol a configurar",role,listOf(Roles.OPERATOR to "Admin operativo",Roles.VIEWER to "Visualizador")){role=it}
                Section("Matriz de acceso"){
                    Mods.list.forEach { (id,label)->val perm=s.permissions.firstOrNull{it.role==role&&it.module==id}
                        if(perm!=null){Text(label,fontWeight=FontWeight.Bold)
                            listOf(Triple("Ver","view",perm.view),Triple("Crear","create",perm.create),Triple("Editar","edit",perm.edit),Triple("Anular","void",perm.void)).forEach{(l,op,v)->
                                Row(verticalAlignment=Alignment.CenterVertically){Text(l,Modifier.weight(1f));Checkbox(v,{task{repo.permission(role,id,op,it)}})}
                            };HorizontalDivider()
                        }
                    }
                }
            }
            "FLAGS"->{
                var months by remember(s.configs){mutableStateOf(s.lifeMonths().toString())}
                Section("Vida útil de corrales"){
                    Field("Meses para distribución de infraestructura",months,{months=it})
                    Button(onClick={task{repo.setInfraLife(months.toIntOrNull()?:0)}}){Text("Guardar vida útil")}
                    Text("El prorrateo por animal es orientativo. Para contabilidad formal consulta a un profesional.",style=MaterialTheme.typography.bodySmall)
                }
                Section("Controles de seguridad"){
                s.flags.forEach{flag->Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){Text(if(flag.name=="ALLOW_LOGIN")"Permitir login" else "Modo mantenimiento")
                    Text(flag.name,style=MaterialTheme.typography.labelSmall)}
                    Switch(flag.value,{task{repo.setFlag(flag.name,it)}})
                }}
                Text("La auditoría de accesos, gastos, ventas y permisos siempre permanece activa.",style=MaterialTheme.typography.bodySmall)
            }}
            "AUDITORIA"->{Section("Últimos movimientos"){
                s.audits.take(100).forEach{a->Column{
                    Text("${s.users.firstOrNull{it.id==a.actor}?.name?:a.actor} · ${a.action}",fontWeight=FontWeight.SemiBold)
                    Text("${a.module}: ${a.details}",style=MaterialTheme.typography.bodySmall)
                    Text(day(a.at),style=MaterialTheme.typography.labelSmall);HorizontalDivider()
                }}
            }}
        }
    }
}
