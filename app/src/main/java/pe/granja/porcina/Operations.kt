package pe.granja.porcina

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pe.granja.porcina.data.*

@Composable
fun LotsPage(s: Snapshot, allowed: suspend (String, String) -> Boolean,
             execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    var desc by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("5") }
    var price by remember { mutableStateOf("750") }
    var weight by remember { mutableStateOf("15") }
    var sex by remember { mutableStateOf("SIN_DEFINIR") }
    Page("Lotes", "Una compra masiva crea todos los chanchos con identificadores CH-###.") {
        CanOperate(allowed, ModuleId.LOTS, "CREAR") { can ->
            if (can) Panel("Nueva compra por lote") {
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción / proveedor") },
                    modifier = Modifier.fillMaxWidth())
                PositiveNumber(count, { count = it }, "Cantidad de animales")
                PositiveNumber(price, { price = it }, "Costo total de compra (S/)")
                PositiveNumber(weight, { weight = it }, "Peso inicial por animal (kg)")
                Selection("Sexo inicial", sex, listOf("SIN_DEFINIR" to "Completar después",
                    "HEMBRA" to "Hembras", "MACHO" to "Machos"), { sex = it })
                Button(onClick = {
                    execute { repo.newLot(desc.ifBlank { "Compra de chanchos" },
                        count.toIntOrNull() ?: 0, Money.cents(price), weight.toDoubleOrNull() ?: 0.0, sex) }
                }, modifier = Modifier.fillMaxWidth()) { Text("Crear lote y animales") }
            }
        }
        Panel("Lotes registrados (${s.lots.size})") {
            s.lots.forEach { lot ->
                val list = s.animals.filter { it.lotId == lot.id }
                InfoLine(lot.code, "${list.count { it.state == "ACTIVO" }} activos / ${list.size} total")
                Text(lot.description, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun AnimalsPage(s: Snapshot, allowed: suspend (String, String) -> Boolean,
                execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    var query by remember { mutableStateOf("") }
    var selectedId by remember { mutableLongStateOf(0L) }
    var newWeight by remember { mutableStateOf("") }
    var newTag by remember { mutableStateOf("") }
    val filtered = s.animals.filter { query.isBlank() || it.code.contains(query, true) ||
        it.tagNumber.contains(query, true) }
    val selected = s.animals.firstOrNull { it.id == selectedId }
    Page("Chanchos y aretes", "Un único inventario distingue comprados y nacidos.") {
        OutlinedTextField(query, { query = it }, label = { Text("Buscar por código / arete") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Panel("Inventario (${filtered.size})") {
            filtered.forEach { a ->
                val state = if (a.state == "ACTIVO") "Activo" else "Vendido"
                OutlinedButton(onClick = { selectedId = a.id; newWeight = a.currentWeightKg.toString();
                    newTag = a.tagNumber }, modifier = Modifier.fillMaxWidth()) {
                    Text("🐖 ${a.code} · arete ${a.tagNumber.ifBlank { "pendiente" }} · $state")
                }
            }
        }
        if (selected != null) Panel("Ficha de ${selected.code}") {
            InfoLine("Código / arete", "${selected.code} / ${selected.tagNumber.ifBlank { "Sin asignar" }}")
            InfoLine("Origen", selected.origin)
            InfoLine("Sexo", selected.sex)
            InfoLine("Lote", s.lots.firstOrNull { it.id == selected.lotId }?.code ?: "—")
            InfoLine("Estado", selected.state)
            InfoLine("Peso inicial", "${selected.initialWeightKg} kg")
            InfoLine("Peso actual", "${selected.currentWeightKg} kg")
            if (ModuleId.EXPENSES in s.modules || ModuleId.REPORTS in s.modules) {
                InfoLine("Compra", Money.format(selected.purchaseCents))
                InfoLine("Gastos asignados", Money.format(s.cost(selected.id)))
                InfoLine("Costo acumulado", Money.format(selected.purchaseCents + s.cost(selected.id)))
            }
            selected.motherId?.let { mid -> InfoLine("Madre", s.animals.firstOrNull { it.id == mid }?.code ?: "$mid") }
            CanOperate(allowed, ModuleId.ANIMALS, "EDITAR") { can ->
                if (can && selected.state == "ACTIVO") {
                    HorizontalDivider()
                    PositiveNumber(newWeight, { newWeight = it }, "Nuevo peso (kg)")
                    Button(onClick = { execute { repo.updateAnimalWeight(selected.id, newWeight.toDoubleOrNull() ?: 0.0) } },
                        modifier = Modifier.fillMaxWidth()) { Text("Actualizar peso") }
                    OutlinedTextField(newTag, { newTag = it }, label = { Text("Número de arete") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedButton(onClick = { execute { repo.changeTag(selected.id, newTag.trim()) } },
                        modifier = Modifier.fillMaxWidth()) { Text("Cambiar arete") }
                }
            }
        }
    }
}

@Composable
fun BirthsPage(s: Snapshot, allowed: suspend (String, String) -> Boolean,
               execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    val mothers = s.animals.filter { it.sex == "HEMBRA" && it.state == "ACTIVO" }
    val fathers = s.animals.filter { it.sex == "MACHO" && it.state == "ACTIVO" }
    var motherId by remember { mutableStateOf("") }
    var fatherId by remember { mutableStateOf("0") }
    var born by remember { mutableStateOf("8") }
    var alive by remember { mutableStateOf("8") }
    Page("Partos y crías", "Las crías vivas se incorporan al inventario con su propia ficha.") {
        CanOperate(allowed, ModuleId.BIRTHS, "CREAR") { can -> if (can) Panel("Registrar parto") {
            Selection("Madre", motherId, mothers.map { it.id.toString() to "${it.code} (arete ${it.tagNumber})" },
                { motherId = it })
            Selection("Padre", fatherId, listOf("0" to "No se conoce") +
                fathers.map { it.id.toString() to it.code }, { fatherId = it })
            PositiveNumber(born, { born = it }, "Total nacidos")
            PositiveNumber(alive, { alive = it }, "Crías vivas")
            Button(onClick = { execute { repo.newBirth(motherId.toLongOrNull() ?: 0,
                fatherId.toLongOrNull()?.takeIf { it > 0 }, born.toIntOrNull() ?: 0,
                alive.toIntOrNull() ?: -1) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Guardar parto y crear crías")
            }
        } }
        Panel("Historial de partos") {
            s.births.forEach { b ->
                InfoLine("Parto #${b.id} · ${s.animals.firstOrNull { it.id == b.motherId }?.code}",
                    "${b.alive}/${b.born} vivas")
                InfoLine("Fecha", date(b.date))
                HorizontalDivider()
            }
            if (s.births.isEmpty()) Text("Sin partos registrados")
        }
    }
}

@Composable
fun ExpensesPage(s: Snapshot, allowed: suspend (String, String) -> Boolean,
                 execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Alimento") }
    var amount by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("LOTE") }
    var paidBy by remember { mutableStateOf("SOCIO_1") }
    var reimbursable by remember { mutableStateOf(true) }
    var animalId by remember { mutableStateOf("") }
    var lotId by remember { mutableStateOf(s.lots.firstOrNull()?.id?.toString() ?: "") }
    var voiding by remember { mutableLongStateOf(0L) }
    var voidReason by remember { mutableStateOf("") }
    Page("Gastos", "Se guarda quién registró, quién pagó y cómo se distribuyó el gasto.") {
        CanOperate(allowed, ModuleId.EXPENSES, "CREAR") { can -> if (can) Panel("Nuevo gasto") {
            OutlinedTextField(description, { description = it }, label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth())
            Selection("Categoría", category, listOf("Alimento", "Vacunas", "Medicina", "Transporte",
                "Agua", "Limpieza", "Mano de obra", "Infraestructura", "Otros").map { it to it },
                { category = it })
            PositiveNumber(amount, { amount = it }, "Monto (S/)")
            Selection("Alcance", scope, listOf("INDIVIDUAL" to "Un chancho",
                "LOTE" to "Todos los activos del lote", "GENERAL" to "Todos los activos de la granja",
                "ACTIVO" to "Infraestructura / activo (sin distribuir aún)"), { scope = it })
            if (scope == "INDIVIDUAL") Selection("Animal", animalId,
                s.animals.filter { it.state == "ACTIVO" }.map { it.id.toString() to it.code }, { animalId = it })
            if (scope == "LOTE") Selection("Lote", lotId,
                s.lots.map { it.id.toString() to it.code }, { lotId = it })
            Selection("Pagado por", paidBy, listOf("SOCIO_1" to "Socio 1 · dinero propio",
                "SOCIO_2" to "Socio 2 · dinero propio", "CAJA" to "Caja del negocio"), {
                paidBy = it; reimbursable = it != "CAJA"
            })
            if (paidBy != "CAJA") Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = reimbursable, onCheckedChange = { reimbursable = it })
                Text("Pendiente de reembolso al socio")
            }
            val targets = when (scope) {
                "INDIVIDUAL" -> 1
                "LOTE" -> s.animals.count { it.lotId.toString() == lotId && it.state == "ACTIVO" }
                "GENERAL" -> s.animals.count { it.state == "ACTIVO" }
                else -> 0
            }
            Text(if (scope == "ACTIVO") "Se registra como activo; no carga costo de los animales."
                else "Distribución automática por cantidad: $targets animales (se redondea a centavos).",
                style = MaterialTheme.typography.bodySmall)
            Button(onClick = { execute {
                repo.newExpense(description, category, Money.cents(amount), scope,
                    animalId.toLongOrNull(), lotId.toLongOrNull(), paidBy, reimbursable)
            } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar gasto") }
        } }
        Panel("Gastos registrados (${s.expenses.size})") {
            s.expenses.forEach { e ->
                Text("${e.category} · ${e.description}")
                InfoLine("Monto", Money.format(e.amountCents) + if (e.voided) " · ANULADO" else "")
                InfoLine("Pagado por", when (e.paidBy) { "SOCIO_1" -> "Socio 1"; "SOCIO_2" -> "Socio 2"; else -> "Caja" })
                InfoLine("Registrado por", s.userNames[e.createdBy] ?: e.createdBy)
                InfoLine("Alcance", e.scope)
                InfoLine("Fecha", date(e.createdAt))
                if (e.id == voiding) {
                    OutlinedTextField(voidReason, { voidReason = it }, label = { Text("Motivo de anulación") })
                    Button(onClick = { execute { repo.voidExpense(e.id, voidReason) }; voiding = 0L }) {
                        Text("Confirmar anulación")
                    }
                } else {
                    CanOperate(allowed, ModuleId.EXPENSES, "ANULAR") { can ->
                        if (can && !e.voided) TextButton(onClick = { voiding = e.id }) { Text("Anular") }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SalesPage(s: Snapshot, allowed: suspend (String, String) -> Boolean,
              execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    var animalId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("90") }
    var buyer by remember { mutableStateOf("") }
    Page("Ventas", "Al vender se registra ingreso y se cambia el estado del animal.") {
        CanOperate(allowed, ModuleId.SALES, "CREAR") { can -> if (can) Panel("Nueva venta") {
            Selection("Animal activo", animalId, s.animals.filter { it.state == "ACTIVO" }
                .map { it.id.toString() to "${it.code} · arete ${it.tagNumber}" }, { animalId = it })
            PositiveNumber(weight, { weight = it }, "Peso final (kg)")
            PositiveNumber(amount, { amount = it }, "Precio total de venta (S/)")
            OutlinedTextField(buyer, { buyer = it }, label = { Text("Comprador") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { execute {
                repo.newSale(animalId.toLongOrNull() ?: 0L, Money.cents(amount),
                    weight.toDoubleOrNull() ?: 0.0, buyer)
            } }, modifier = Modifier.fillMaxWidth()) { Text("Registrar venta") }
        } }
        Panel("Ventas registradas") {
            s.sales.forEach { sale ->
                val a = s.animals.firstOrNull { it.id == sale.animalId }
                InfoLine(a?.code ?: "#${sale.animalId}", Money.format(sale.amountCents))
                InfoLine("Peso", "${sale.weightKg} kg")
                InfoLine("Comprador", sale.buyer.ifBlank { "Sin nombre" })
                InfoLine("Ganancia estimada", Money.format(sale.amountCents - (a?.purchaseCents ?: 0) - s.cost(sale.animalId)))
                HorizontalDivider()
            }
            if (s.sales.isEmpty()) Text("Todavía no hay ventas")
        }
    }
}
