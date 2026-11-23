package pe.granja.porcina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import pe.granja.porcina.data.*
import pe.granja.porcina.ui.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Green=Color(0xFF197146)
val BG=Color(0xFFF7F8F5)
val Orange=Color(0xFFE69C45)
fun day(v:Long?)=if(v==null)"Sin fecha" else SimpleDateFormat("dd/MM/yyyy",Locale("es","PE")).format(Date(v))
fun age(birth:Long?):String {
    if(birth==null) return "Edad no indicada"
    val days=((System.currentTimeMillis()-birth)/86400000).coerceAtLeast(0)
    return if(days<60) "$days días" else "${days/30} meses"
}

class MainActivity:ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        // BD separada de la demo anterior: conserva la demo, la versión real comienza sin registros de negocio.
        val db=Room.databaseBuilder(applicationContext,AgroDb::class.java,"tirios_agro_local_v2.db").build()
        val repo=AgroRepository(db)
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Green,secondary=Orange,background=BG,
                surface=Color.White), shapes=Shapes(medium=RoundedCornerShape(16.dp))) {
                Root(repo)
            }
        }
    }
}

@Composable
fun Root(repo:AgroRepository) {
    val scope=rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<AgroSnapshot?>(null) }
    var page by remember { mutableStateOf(Mods.HOME) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var showNotices by remember { mutableStateOf(false) }
    var showTour by remember { mutableStateOf(false) }
    var tourAt by remember { mutableIntStateOf(0) }
    var detail by remember { mutableStateOf<Pair<String,Long>?>(null) }

    LaunchedEffect(Unit){ runCatching{repo.initialize()}.onFailure{error=it.message?:"Error de base de datos"};ready=true }
    fun task(block:suspend()->Unit){ scope.launch {
        busy=true
        runCatching{block();state=repo.refresh()}.onFailure{error=it.message?:"Error inesperado"}
        busy=false
    } }
    if(!ready){ Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()};return }
    if(!logged){ Login(error,busy){ email,pass-> scope.launch {
        busy=true
        runCatching { val message=repo.login(email,pass);if(message.isNotEmpty()) error=message else {
            state=repo.refresh();logged=true;page=Mods.HOME;error="";showTour=!state!!.user.onboardingCompleted;tourAt=0
        } }.onFailure { error=it.message?:"Error" }
        busy=false
    } };return }
    val s=state?:return
    val nav=Mods.list.filter{it.first in s.modules}
    Scaffold(
        topBar={ Surface(color=Green,shadowElevation=2.dp){Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){Text("TIRIOS AGRO",color=Color.White,fontWeight=FontWeight.Bold)
                Text("Gestión Porcina · ${s.user.name}",color=Color.White.copy(alpha=.84f),style=MaterialTheme.typography.labelSmall)}
            Box{ IconButton(onClick={showNotices=true}) {Icon(Icons.Default.Notifications,"Notificaciones",tint=Color.White)}
                if(s.notices.any{!it.read}) Badge(Modifier.align(Alignment.TopEnd)){ Text("${s.notices.count{!it.read}}") }
            }
            IconButton(onClick={showTour=true;tourAt=0}){Icon(Icons.Default.HelpOutline,"Ayuda",tint=Color.White)}
            IconButton(onClick={scope.launch{repo.logout();logged=false;state=null}}){Icon(Icons.Default.Logout,"Salir",tint=Color.White)}
        }}},
        bottomBar={ Surface(shadowElevation=4.dp){ Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(6.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp)) { nav.forEach {(id,label)->
            FilterChip(selected=page==id,onClick={page=id;detail=null},label={Text(label,maxLines=1)},leadingIcon={ModIcon(id)})
        }}}}
    ){pad -> Column(Modifier.fillMaxSize().padding(pad)){
        if(error.isNotBlank()) Surface(color=Color(0xFFFFEBE8)){Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically){
            Text(error,color=Color(0xFF8D2D22),modifier=Modifier.weight(1f));TextButton(onClick={error=""}){Text("Cerrar")}}}
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when(page){
            Mods.HOME -> HomePage(s,onNavigate={page=it},onDetail={kind,id->detail=kind to id})
            Mods.INVENTORY -> InventoryPage(s,repo,::task,onDetail={kind,id->detail=kind to id})
            Mods.EXPENSES -> OutlaysPage(s,repo,::task,onDetail={kind,id->detail=kind to id})
            Mods.PARTNERS -> PartnersPage(s,repo,::task)
            Mods.REPORTS -> ReportsPage(s,onDetail={kind,id->detail=kind to id})
            Mods.ADMIN -> AdminPage(s,repo,::task)
        }
    }}
    if(showTour) {
        val steps=listOf("¡Bienvenido a TIRIOS AGRO!" to "Controla tu granja y sus resultados desde un solo lugar.",
            "Inicio" to "Consulta animales activos, ventas, costos y notificaciones.",
            "Inventario" to "Registra lotes, aretes, crías y alimento. Vende desde cada animal.",
            "Gastos y compras" to "Anota qué compraste, para qué, quién lo pagó y su comprobante.",
            "Socios y capital" to "Crea los socios y compara cuánto desembolsó cada uno.",
            "Reportes" to "Consulta los resultados y exporta un PDF local.",
            "Administración" to "Gestiona usuarios, permisos y el historial de auditoría.")
        val visible=steps.filterIndexed{index,_-> index==0 || index==1 || (index==2&&Mods.INVENTORY in s.modules) ||
            (index==3&&Mods.EXPENSES in s.modules)||(index==4&&Mods.PARTNERS in s.modules)||(index==5&&Mods.REPORTS in s.modules)||(index==6&&Mods.ADMIN in s.modules)}
        val item=visible[tourAt.coerceIn(0,visible.lastIndex)]
        AlertDialog(onDismissRequest={},title={Text(item.first)},text={Text(item.second)},
            confirmButton={TextButton(onClick={if(tourAt>=visible.lastIndex){showTour=false;task{repo.finishTour()}}else tourAt++}){
                Text(if(tourAt>=visible.lastIndex)"Empezar" else "Siguiente (${tourAt+1}/${visible.size})")}},
            dismissButton={TextButton(onClick={showTour=false;task{repo.finishTour()}}){Text("Omitir")}})
    }
    if(showNotices) AlertDialog(onDismissRequest={showNotices=false},title={Text("Notificaciones")},
        text={Column(Modifier.heightIn(max=450.dp).verticalScroll(rememberScrollState())) {
            if(s.notices.isEmpty()) Text("Todavía no hay movimientos.")
            s.notices.take(35).forEach { n ->
                TextButton(onClick={showNotices=false;task{repo.readNotice(n.id)};page=n.module
                    if(n.entityId!=null) {
                        val target=when {
                            n.module==Mods.EXPENSES -> "GASTO"
                            n.module==Mods.INVENTORY && n.text.contains("VENTA:",ignoreCase=true) -> "VENTA"
                            n.module==Mods.INVENTORY && (n.text.contains("EDICIÓN:") || n.text.contains("FALLECIMIENTO:") || n.text.contains("PARTO:")) -> "ANIMAL"
                            else -> null
                        }
                        if(target!=null) detail=target to n.entityId
                    }}){
                    Column {Text((if(n.read)"" else "● ")+n.text);Text(day(n.at),style=MaterialTheme.typography.labelSmall)}
                };HorizontalDivider()
            }
        }},confirmButton={TextButton(onClick={showNotices=false}){Text("Cerrar")}})
    if(detail!=null){ val (kind,id)=detail!!
        DetailPopup(s,kind,id,onDismiss={detail=null},onSell={amount,kg,buyer->task{repo.sell(id,Soles.parse(amount),kg.toDoubleOrNull()?:0.0,buyer)};detail=null},
            onEditAnimal={tag,sex,stage,weight,birth,notes->task{
                val birthAt=if(birth.isBlank()) null else java.time.LocalDate.parse(birth).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                repo.updateAnimal(id,tag,sex,stage,weight.toDoubleOrNull()?:0.0,birthAt,notes)
            };detail=null},
            onDeath={why,note->task{repo.death(id,System.currentTimeMillis(),why,note)};detail=null},
            onVoid={reason->task{repo.voidOutlay(id,reason)};detail=null})
    }
}

@Composable fun ModIcon(id:String){val icon=when(id){Mods.HOME->Icons.Default.Home;Mods.INVENTORY->Icons.Default.Inventory2;
    Mods.EXPENSES->Icons.Default.ReceiptLong;Mods.PARTNERS->Icons.Default.Groups;
    Mods.REPORTS->Icons.Default.Assessment;else->Icons.Default.AdminPanelSettings}
    Icon(icon,null,modifier=Modifier.size(17.dp))}

@Composable fun Login(error:String,busy:Boolean,onLogin:(String,String)->Unit){
    var email by remember{mutableStateOf("admin@granja.local")};var pass by remember{mutableStateOf("")}
    Column(Modifier.fillMaxSize().background(BG).verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){
        Image(painterResource(R.drawable.logo_tirios),"Logo de TIRIOS AGRO",Modifier.size(132.dp))
        Spacer(Modifier.height(10.dp));Text("TIRIOS AGRO",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,color=Green)
        Text("Gestión Porcina",color=Color.Gray)
        Spacer(Modifier.height(26.dp));OutlinedTextField(email,{email=it},modifier=Modifier.fillMaxWidth(),label={Text("Correo")},singleLine=true)
        OutlinedTextField(pass,{pass=it},modifier=Modifier.fillMaxWidth(),label={Text("Contraseña")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
        if(error.isNotBlank()) Text(error,color=Color.Red,modifier=Modifier.padding(8.dp))
        Spacer(Modifier.height(12.dp));Button(onClick={onLogin(email,pass)},enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Ingresar")}
        Spacer(Modifier.height(18.dp));Text("Cuentas para validación de roles",style=MaterialTheme.typography.labelMedium)
        listOf(Triple("Admin","admin@granja.local","Admin1234!"),Triple("Operativo","socio@granja.local","Socio1234!"),Triple("Lectura","visor@granja.local","Visor1234!")).forEach{(title,user,pwd)->
            TextButton(onClick={email=user;pass=pwd}){Text(title)}
        }
        Text("Los movimientos se guardan solo en este celular.",color=Color.Gray,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable fun Section(title:String,body:@Composable ColumnScope.()->Unit){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Color.White)){
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text(title,fontWeight=FontWeight.Bold,color=Green,style=MaterialTheme.typography.titleMedium);body()
        }
    }
}
@Composable fun ScrollPage(title:String,body:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(title,color=Green,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold);body()
    }
}
@Composable fun Field(label:String,value:String,change:(String)->Unit,modifier:Modifier=Modifier){
    OutlinedTextField(value,change,label={Text(label)},modifier=modifier.fillMaxWidth(),singleLine=true)
}
@Composable fun Choice(label:String,value:String,options:List<Pair<String,String>>,change:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    Box { OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
        Text("$label: ${options.firstOrNull{it.first==value}?.second?:value}",maxLines=1); Spacer(Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)
    };DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){options.forEach{(id,text)->
        DropdownMenuItem(text={Text(text)},onClick={change(id);expanded=false})}} }
}
@Composable fun Stat(title:String,value:String,modifier:Modifier=Modifier){
    Card(modifier,colors=CardDefaults.cardColors(containerColor=Color(0xFFEAF5EC))){Column(Modifier.padding(13.dp)){
        Text(title,style=MaterialTheme.typography.labelMedium,color=Green);Spacer(Modifier.height(6.dp));
        Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,color=Green)}}
}
@Composable fun Line(label:String,value:String){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
    Text(label,modifier=Modifier.weight(1f));Text(value,fontWeight=FontWeight.SemiBold)}}
