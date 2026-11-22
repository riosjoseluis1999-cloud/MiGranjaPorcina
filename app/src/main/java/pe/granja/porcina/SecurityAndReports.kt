package pe.granja.porcina

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pe.granja.porcina.data.*

@Composable
fun ReportsPage(s: Snapshot) {
    val sales = s.sales.filterNot { it.voided }
    val expenses = s.expenses.filterNot { it.voided }
    Page("Rentabilidad", "Utilidad realizada basada en los animales vendidos; inversión y caja por separado.") {
        Panel("Resultados generales") {
            InfoLine("Ventas", Money.format(sales.sumOf { it.amountCents }))
            InfoLine("Compras (todos los animales)", Money.format(s.animals.sumOf { it.purchaseCents }))
            InfoLine("Gastos operativos", Money.format(expenses.filter { it.scope != "ACTIVO" }.sumOf { it.amountCents }))
            InfoLine("Activos / corrales", Money.format(expenses.filter { it.scope == "ACTIVO" }.sumOf { it.amountCents }))
            InfoLine("UTILIDAD REALIZADA ESTIMADA", Money.format(s.realizedProfit()))
            Text("Nota: la infraestructura queda como activo; para una rentabilidad contable completa falta añadir depreciación y otros ajustes.",
                style = MaterialTheme.typography.labelSmall)
        }
        Panel("Gastos pagados por cada socio") {
            InfoLine("Socio 1", Money.format(s.paidBy("SOCIO_1")))
            InfoLine("Socio 2", Money.format(s.paidBy("SOCIO_2")))
            InfoLine("Caja del negocio", Money.format(s.paidBy("CAJA")))
            HorizontalDivider()
            InfoLine("Pendiente reembolso Socio 1", Money.format(s.reimbursableBy("SOCIO_1")))
            InfoLine("Pendiente reembolso Socio 2", Money.format(s.reimbursableBy("SOCIO_2")))
            Text("Los desembolsos de los socios no se duplican como gastos ni cambian automáticamente el porcentaje de propiedad.",
                style = MaterialTheme.typography.labelSmall)
        }
        Panel("Detalle por lote") {
            s.lots.forEach { lot ->
                val ids = s.animals.filter { it.lotId == lot.id }.map { it.id }.toSet()
                val lotSales = sales.filter { it.animalId in ids }
                val costs = s.animals.filter { it.id in ids }.sumOf { it.purchaseCents + s.cost(it.id) }
                val soldCosts = s.animals.filter { it.id in ids && lotSales.any { sale -> sale.animalId == it.id } }
                    .sumOf { it.purchaseCents + s.cost(it.id) }
                Text(lot.code, fontWeight = FontWeight.Bold)
                InfoLine("Animales", "${ids.size}")
                InfoLine("Costo acumulado (incluye no vendidos)", Money.format(costs))
                InfoLine("Ventas", Money.format(lotSales.sumOf { it.amountCents }))
                InfoLine("Utilidad realizada estimada", Money.format(lotSales.sumOf { it.amountCents } - soldCosts))
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun PartnersPage(s: Snapshot) {
    Page("Socios y aportes", "Los aportes se distinguen de los gastos pagados de bolsillo.") {
        s.partners.forEach { partner ->
            Panel("${partner.name} · ${partner.sharePercent}%") {
                InfoLine("Capital aportado", Money.format(s.contributions.filter { it.partnerId == partner.id }.sumOf { it.amountCents }))
                InfoLine("Desembolsos propios registrados", Money.format(s.paidBy(partner.id)))
                InfoLine("Declarado pendiente de reembolso", Money.format(s.reimbursableBy(partner.id)))
                InfoLine("Parte teórica de utilidad realizada", Money.format(s.realizedProfit() * partner.sharePercent / 100))
            }
        }
        Panel("Importante") {
            Text("Un pago personal de alimento no debe contarse como nuevo aporte de capital salvo que ambos socios lo acuerden. El saldo de reembolso es informativo: esta demo todavía no registra reembolsos efectuados ni retiros de utilidades.")
        }
    }
}

@Composable
fun UsersPage(s: Snapshot, execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    var role by remember { mutableStateOf(RoleId.OPERATIVO) }
    val admin = s.user.roleId == RoleId.PRINCIPAL
    Page("Usuarios y roles", "Permisos RBAC por identificador de módulo y operación.") {
        Panel("Usuarios") {
            s.users.forEach { user ->
                InfoLine(user.name, if (user.enabled) "Habilitado" else "Bloqueado")
                Text(user.email + " · " + RoleId.label(user.roleId),
                    style = MaterialTheme.typography.bodySmall)
                if (admin && user.id != "U001") {
                    OutlinedButton(onClick = { execute { repo.blockUser(user.id, !user.enabled) } },
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (user.enabled) "Bloquear a ${user.name}" else "Habilitar a ${user.name}")
                    }
                }
                HorizontalDivider()
            }
        }
        if (admin) Panel("Matriz editable de permisos") {
            Selection("Rol", role, listOf(RoleId.OPERATIVO to "Administrador operativo",
                RoleId.VISOR to "Visualizador"), { role = it })
            ModuleId.all.forEach { (id, title) ->
                val p = s.permissions.firstOrNull { it.roleId == role && it.moduleId == id }
                if (p != null) {
                    Text("$title ($id)", fontWeight = FontWeight.SemiBold)
                    listOf("VER" to p.canView, "CREAR" to p.canCreate,
                        "EDITAR" to p.canEdit, "ANULAR" to p.canVoid).chunked(2).forEach { pair ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            pair.forEach { (op, checked) ->
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Checkbox(checked = checked,
                                        onCheckedChange = { value -> execute { repo.setPermission(role, id, op, value) } })
                                    Text(op, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun AuditPage(s: Snapshot) {
    Page("Auditoría", "Registro inmutable desde la interfaz de accesos y modificaciones.") {
        Panel("Últimos 200 movimientos") {
            s.audits.forEach { audit ->
                Text("${audit.action} · ${audit.moduleId}", fontWeight = FontWeight.Bold)
                Text("Usuario: ${s.userNames[audit.actorUserId] ?: audit.actorUserId}")
                Text(audit.detail)
                Text(date(audit.createdAt), style = MaterialTheme.typography.labelSmall)
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun SettingsPage(s: Snapshot, execute: (suspend () -> Unit) -> Unit, repo: GranjaRepository) {
    val admin = s.user.roleId == RoleId.PRINCIPAL
    val login = s.settings.firstOrNull { it.key == "ALLOW_LOGIN" }?.value == "true"
    val maintenance = s.settings.firstOrNull { it.key == "MAINTENANCE" }?.value == "true"
    Page("Configuración de seguridad", "Interruptores globales para probar restricciones.") {
        Panel("Control de accesos") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Permitir nuevos ingresos")
                    Text("Si deshabilitas, solo entra el admin principal para recuperar acceso.",
                        style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = login, onCheckedChange = { execute { repo.setFlag("ALLOW_LOGIN", it) } }, enabled = admin)
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Modo mantenimiento")
                    Text("Bloquea ingreso y escrituras de no principales.",
                        style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = maintenance, onCheckedChange = { execute { repo.setFlag("MAINTENANCE", it) } }, enabled = admin)
            }
            HorizontalDivider()
            InfoLine("Auditoría obligatoria", "ACTIVA")
            Text("No es posible desactivar la auditoría crítica. Se conserva registro de cambios de roles, accesos y anulaciones.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
