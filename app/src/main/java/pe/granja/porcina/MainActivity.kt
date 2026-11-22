package pe.granja.porcina

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.Room
import kotlinx.coroutines.launch
import pe.granja.porcina.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Forest = Color(0xFF1E7259)
private val Cream = Color(0xFFFBF9F3)
private val Ink = Color(0xFF263C34)
private val Coral = Color(0xFFE6A056)
private val dayFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "PE"))
fun date(ms: Long) = dayFormat.format(Date(ms))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, GranjaDatabase::class.java, "granja_local.db")
            .build()
        val repo = GranjaRepository(database)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Forest, background = Cream,
                onBackground = Ink, secondary = Coral), shapes = Shapes(medium = RoundedCornerShape(14.dp))) {
                GranjaApp(repo)
            }
        }
    }
}

@Composable
fun GranjaApp(repo: GranjaRepository) {
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf(false) }
    var snapshot by remember { mutableStateOf<Snapshot?>(null) }
    var currentScreen by remember { mutableStateOf(ModuleId.DASHBOARD) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { repo.seedIfEmpty() }.onFailure { error = it.message ?: "Error al iniciar base de datos" }
        ready = true
    }
    fun runTask(task: suspend () -> Unit) {
        scope.launch {
            busy = true
            runCatching {
                task()
                snapshot = repo.snapshot()
            }.onFailure { error = it.message ?: "Error inesperado" }
            busy = false
        }
    }
    if (!ready) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (!logged) {
        LoginPage(error = error, busy = busy, onLogin = { email, pwd ->
            scope.launch {
                busy = true
                val result = runCatching { repo.login(email, pwd) }.getOrElse { it.message ?: "Error" }
                if (result.isEmpty()) {
                    snapshot = repo.snapshot()
                    currentScreen = ModuleId.DASHBOARD
                    logged = true
                    error = ""
                } else error = result
                busy = false
            }
        })
        return
    }
    val snap = snapshot ?: return
    val visibleModules = ModuleId.all.filter { it.first in snap.modules }
    Scaffold(
        topBar = {
            Surface(color = Forest, shadowElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("🐷 Mi Granja Porcina", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("${snap.user.name} · ${RoleId.label(snap.user.roleId)}",
                            color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = {
                        scope.launch {
                            repo.logout()
                            snapshot = null
                            logged = false
                            error = ""
                        }
                    }) { Icon(Icons.Default.Logout, "Cerrar sesión", tint = Color.White) }
                }
            }
        },
        bottomBar = {
            Surface(shadowElevation = 5.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().horizontalScroll(rememberScrollState()).padding(7.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visibleModules.forEach { (id, label) ->
                        FilterChip(selected = currentScreen == id, onClick = { currentScreen = id },
                            label = { Text(label, maxLines = 1) })
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (error.isNotBlank()) {
                Surface(color = Color(0xFFFFECE8)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(error, color = Color(0xFF95321E), modifier = Modifier.weight(1f))
                        IconButton(onClick = { error = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Cerrar error", tint = Color(0xFF95321E))
                        }
                    }
                }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (currentScreen !in snap.modules) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No tienes permiso para este módulo") }
            } else {
                when (currentScreen) {
                    ModuleId.DASHBOARD -> Dashboard(snap)
                    ModuleId.ANIMALS -> AnimalsPage(snap, repo::allowed, ::runTask, repo)
                    ModuleId.LOTS -> LotsPage(snap, repo::allowed, ::runTask, repo)
                    ModuleId.BIRTHS -> BirthsPage(snap, repo::allowed, ::runTask, repo)
                    ModuleId.EXPENSES -> ExpensesPage(snap, repo::allowed, ::runTask, repo)
                    ModuleId.SALES -> SalesPage(snap, repo::allowed, ::runTask, repo)
                    ModuleId.REPORTS -> ReportsPage(snap)
                    ModuleId.PARTNERS -> PartnersPage(snap)
                    ModuleId.USERS -> UsersPage(snap, ::runTask, repo)
                    ModuleId.AUDIT -> AuditPage(snap)
                    ModuleId.SETTINGS -> SettingsPage(snap, ::runTask, repo)
                }
            }
        }
    }
}

@Composable
private fun LoginPage(error: String, busy: Boolean, onLogin: (String, String) -> Unit) {
    var email by remember { mutableStateOf("admin@granja.local") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Cream).verticalScroll(rememberScrollState())
        .padding(28.dp), verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🐷", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("Mi Granja Porcina", style = MaterialTheme.typography.headlineMedium, color = Forest,
            fontWeight = FontWeight.Bold)
        Text("Demo local · sin internet · SQLite / Room", color = Ink.copy(alpha = .75f))
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo de prueba") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth())
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onLogin(email, password) }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Iniciar sesión") }
        Spacer(Modifier.height(20.dp))
        Text("Selecciona una cuenta de prueba", fontWeight = FontWeight.SemiBold)
        listOf(
            Triple("Admin principal", "admin@granja.local", "Admin1234!"),
            Triple("Admin operativo", "socio@granja.local", "Socio1234!"),
            Triple("Visualizador", "visor@granja.local", "Visor1234!")
        ).forEach { (title, user, pass) ->
            OutlinedButton(onClick = { email = user; password = pass },
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)) { Text(title) }
        }
        Text("Usuarios ficticios. No utilizar estas claves en producción.",
            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
fun Page(title: String, subtitle: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Forest, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall,
            color = Ink.copy(alpha = .7f))
        content()
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Selection(label: String, current: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${options.firstOrNull { it.first == current }?.second ?: "Elegir"}",
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
fun PositiveNumber(value: String, onChange: (String) -> Unit, label: String) =
    OutlinedTextField(value, onChange, label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true, modifier = Modifier.fillMaxWidth())

@Composable
fun CanOperate(repoAllowed: suspend (String, String) -> Boolean,
               module: String, operation: String, body: @Composable (Boolean) -> Unit) {
    var allowed by remember(module, operation) { mutableStateOf(false) }
    LaunchedEffect(module, operation) { allowed = repoAllowed(module, operation) }
    body(allowed)
}

@Composable
fun Dashboard(s: Snapshot) {
    Page("Resumen de la granja", "Indicadores de la operación. Los importes son en soles.") {
        val active = s.animals.count { it.state == "ACTIVO" }
        val born = s.animals.count { it.origin == "NACIDO" }
        val incomes = s.sales.filterNot { it.voided }.sumOf { it.amountCents }
        val operations = s.expenses.filter { !it.voided && it.scope != "ACTIVO" }.sumOf { it.amountCents }
        val assets = s.expenses.filter { !it.voided && it.scope == "ACTIVO" }.sumOf { it.amountCents }
        val initial = s.contributions.sumOf { it.amountCents }
        Panel("Inventario") {
            InfoLine("Animales activos", "$active")
            InfoLine("Nacidos en la granja", "$born")
            InfoLine("Lotes", "${s.lots.size}")
        }
        if (ModuleId.REPORTS in s.modules) Panel("Resultado financiero") {
            InfoLine("Capital aportado", Money.format(initial))
            InfoLine("Infraestructura registrada (activos)", Money.format(assets))
            InfoLine("Gastos operativos", Money.format(operations))
            InfoLine("Ventas", Money.format(incomes))
            InfoLine("Utilidad realizada estimada", Money.format(s.realizedProfit()))
            Text("La inversión en corrales es un activo y no se carga completa a la primera venta. La depreciación aún no se calcula.",
                style = MaterialTheme.typography.labelSmall)
        }
        Panel("Cómo usar esta demo") {
            Text("1. En Lotes agrega una compra masiva.")
            Text("2. En Chanchos actualiza pesos y aretes.")
            Text("3. Registra partos, gastos individuales o compartidos y ventas.")
            Text("4. Compara pagos de socios y revisa la rentabilidad.")
            Text("5. Con el Admin principal configura permisos, bloqueos y consulta auditoría.")
        }
    }
}
