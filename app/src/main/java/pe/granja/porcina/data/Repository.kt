package pe.granja.porcina.data

import androidx.room.withTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object Money {
    fun cents(input: String): Long {
        val amount = BigDecimal(input.trim().replace(',', '.')).setScale(2, RoundingMode.HALF_UP)
        require(amount > BigDecimal.ZERO) { "El monto debe ser mayor a cero" }
        return amount.movePointRight(2).longValueExact()
    }
    fun format(cents: Long) = "S/ " + BigDecimal.valueOf(cents, 2).setScale(2).toPlainString()
}

object Passwords {
    fun salt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.let { Base64.getEncoder().encodeToString(it) }
    fun digest(pass: String, salt: String): String {
        val spec = PBEKeySpec(pass.toCharArray(), Base64.getDecoder().decode(salt), 120_000, 256)
        return try {
            Base64.getEncoder().encodeToString(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded)
        } finally { spec.clearPassword() }
    }
    fun verify(pass: String, salt: String, stored: String): Boolean =
        java.security.MessageDigest.isEqual(
            Base64.getDecoder().decode(digest(pass, salt)), Base64.getDecoder().decode(stored)
        )
}

class GranjaRepository(private val db: GranjaDatabase) {
    private val dao = db.dao()
    var currentUser: UserEntity? = null
        private set

    suspend fun seedIfEmpty() = db.withTransaction {
        if (dao.users().isNotEmpty()) return@withTransaction
        val samples = listOf(
            Triple("U001", "Administrador principal", Pair("admin@granja.local", "Admin1234!")),
            Triple("U002", "Administrador operativo", Pair("socio@granja.local", "Socio1234!")),
            Triple("U003", "Visualizador", Pair("visor@granja.local", "Visor1234!"))
        )
        val roles = listOf(RoleId.PRINCIPAL, RoleId.OPERATIVO, RoleId.VISOR)
        samples.forEachIndexed { i, sample ->
            val salt = Passwords.salt()
            dao.saveUsers(UserEntity(sample.first, sample.second, sample.third.first,
                Passwords.digest(sample.third.second, salt), salt, roles[i]))
        }
        ModuleId.all.forEach { (module, _) ->
            dao.savePermissions(PermissionEntity(RoleId.PRINCIPAL, module, true, true, true, true))
            val operative = module in listOf(ModuleId.DASHBOARD, ModuleId.ANIMALS, ModuleId.LOTS,
                ModuleId.BIRTHS, ModuleId.EXPENSES, ModuleId.SALES, ModuleId.REPORTS, ModuleId.PARTNERS)
            val write = module in listOf(ModuleId.ANIMALS, ModuleId.LOTS, ModuleId.BIRTHS,
                ModuleId.EXPENSES, ModuleId.SALES)
            dao.savePermissions(PermissionEntity(RoleId.OPERATIVO, module, operative, write, write, false))
            dao.savePermissions(PermissionEntity(RoleId.VISOR, module,
                module in listOf(ModuleId.DASHBOARD, ModuleId.ANIMALS, ModuleId.LOTS, ModuleId.BIRTHS),
                false, false, false))
        }
        dao.saveSettings(SettingEntity("ALLOW_LOGIN", "true"),
            SettingEntity("MAINTENANCE", "false"), SettingEntity("AUDIT_ENABLED", "true"))
        dao.savePartners(PartnerEntity("SOCIO_1", "Socio 1", 50), PartnerEntity("SOCIO_2", "Socio 2", 50))
        val now = System.currentTimeMillis()
        dao.addContribution(ContributionEntity(partnerId = "SOCIO_1", amountCents = 300_000,
            note = "Capital inicial", createdAt = now, createdBy = "U001"))
        dao.addContribution(ContributionEntity(partnerId = "SOCIO_2", amountCents = 300_000,
            note = "Capital inicial", createdAt = now, createdBy = "U002"))
        val lotId = dao.addLot(LotEntity(description = "Primera compra - 20 chanchos", createdAt = now, createdBy = "U001"))
        dao.updateLot(LotEntity(id = lotId, code = "L-%03d".format(lotId), description = "Primera compra - 20 chanchos", createdAt = now, createdBy = "U001"))
        repeat(20) { i ->
            val id = dao.addAnimal(AnimalEntity(lotId = lotId,
                sex = if (i % 2 == 0) "HEMBRA" else "MACHO", initialWeightKg = 15.0,
                currentWeightKg = 15.0, purchaseCents = 15_000, createdAt = now, createdBy = "U001"))
            val animal = dao.animal(id)!!
            dao.updateAnimal(animal.copy(code = "CH-%03d".format(id), tagNumber = "%03d".format(id)))
        }
        // Los corrales son infraestructura (activo), NO gasto consumido del primer lote.
        dao.addExpense(ExpenseEntity(category = "Infraestructura", description = "Construcción de corrales (activo)",
            amountCents = 200_000, scope = "ACTIVO", paidBy = "CAJA", createdAt = now, createdBy = "U001"))
        addExpenseInternal(ExpenseEntity(category = "Alimento", description = "Concentrado lote 001",
            amountCents = 40_000, scope = "LOTE", lotId = lotId,
            paidBy = "SOCIO_1", reimbursable = true, createdAt = now, createdBy = "U001"))
        addExpenseInternal(ExpenseEntity(category = "Vacunas", description = "Campaña de vacunación",
            amountCents = 25_000, scope = "LOTE", lotId = lotId,
            paidBy = "SOCIO_2", reimbursable = true, createdAt = now, createdBy = "U002"))
        addAuditInternal("U001", "SEED", ModuleId.SETTINGS, "Demo: 3 usuarios, 2 socios, 20 animales, 3 gastos")
    }

    private suspend fun addAuditInternal(actor: String, action: String, module: String, detail: String) {
        dao.addAudit(AuditEntity(actorUserId = actor, action = action, moduleId = module,
            detail = detail, createdAt = System.currentTimeMillis()))
    }

    suspend fun login(email: String, password: String): String {
        val candidate = dao.userByEmail(email.trim().lowercase())
        if (candidate == null || !Passwords.verify(password, candidate.salt, candidate.passwordHash)) {
            addAuditInternal(candidate?.id ?: "DESCONOCIDO", "LOGIN_RECHAZADO", ModuleId.USERS, "Credenciales incorrectas")
            return "Credenciales incorrectas"
        }
        if (!candidate.enabled) {
            addAuditInternal(candidate.id, "LOGIN_BLOQUEADO", ModuleId.USERS, "Cuenta deshabilitada")
            return "Esta cuenta está bloqueada"
        }
        val allow = dao.setting("ALLOW_LOGIN")?.value == "true"
        val maintenance = dao.setting("MAINTENANCE")?.value == "true"
        if ((!allow || maintenance) && candidate.roleId != RoleId.PRINCIPAL) {
            addAuditInternal(candidate.id, "LOGIN_BLOQUEADO", ModuleId.SETTINGS, "Acceso temporalmente suspendido")
            return "El inicio de sesión está suspendido"
        }
        currentUser = candidate
        addAuditInternal(candidate.id, "LOGIN_OK", ModuleId.USERS, "Ingreso autorizado")
        return ""
    }

    suspend fun logout() {
        currentUser?.let { addAuditInternal(it.id, "LOGOUT", ModuleId.USERS, "Cierre de sesión") }
        currentUser = null
    }

    suspend fun allowed(module: String, operation: String): Boolean {
        val u = currentUser ?: return false
        if (u.roleId == RoleId.PRINCIPAL) return true
        val p = dao.permission(u.roleId, module) ?: return false
        return when (operation) {
            "VER" -> p.canView
            "CREAR" -> p.canCreate
            "EDITAR" -> p.canEdit
            "ANULAR" -> p.canVoid
            else -> false
        }
    }

    private suspend fun requireAccess(module: String, operation: String) {
        check(allowed(module, operation)) { "Sin permiso para $operation en $module" }
        val user = currentUser ?: error("Sesión no iniciada")
        check(dao.user(user.id)?.enabled == true) { "Tu cuenta fue deshabilitada" }
        if (user.roleId != RoleId.PRINCIPAL) {
            check(dao.setting("MAINTENANCE")?.value != "true") { "Sistema en mantenimiento" }
        }
    }

    suspend fun snapshot(): Snapshot {
        val user = currentUser ?: error("Sesión no iniciada")
        val permitted = ModuleId.all.filter { allowed(it.first, "VER") }.map { it.first }.toSet()
        return Snapshot(user = user, modules = permitted,
            userNames = dao.users().associate { it.id to it.name },
            users = if (ModuleId.USERS in permitted) dao.users() else emptyList(),
            permissions = if (ModuleId.USERS in permitted) dao.permissions() else emptyList(),
            settings = if (ModuleId.SETTINGS in permitted) listOfNotNull(dao.setting("ALLOW_LOGIN"),
                dao.setting("MAINTENANCE"), dao.setting("AUDIT_ENABLED")) else emptyList(),
            partners = if (ModuleId.PARTNERS in permitted) dao.partners() else emptyList(),
            contributions = if (ModuleId.PARTNERS in permitted) dao.contributions() else emptyList(),
            lots = if (ModuleId.LOTS in permitted || ModuleId.ANIMALS in permitted) dao.lots() else emptyList(),
            animals = if (permitted.any { it in listOf(ModuleId.ANIMALS,ModuleId.LOTS,ModuleId.BIRTHS) }) dao.animals() else emptyList(),
            births = if (ModuleId.BIRTHS in permitted) dao.births() else emptyList(),
            expenses = if (ModuleId.EXPENSES in permitted || ModuleId.REPORTS in permitted) dao.expenses() else emptyList(),
            allocations = if (ModuleId.REPORTS in permitted || ModuleId.EXPENSES in permitted || ModuleId.ANIMALS in permitted) dao.allocations() else emptyList(),
            sales = if (ModuleId.SALES in permitted || ModuleId.REPORTS in permitted) dao.sales() else emptyList(),
            audits = if (ModuleId.AUDIT in permitted) dao.audits() else emptyList())
    }

    suspend fun newLot(description: String, quantity: Int, priceTotal: Long, initialWeight: Double, sex: String) = db.withTransaction {
        requireAccess(ModuleId.LOTS, "CREAR")
        require(quantity in 1..500) { "Cantidad entre 1 y 500" }
        require(priceTotal >= 0) { "Costo negativo" }
        val u = currentUser!!
        val now = System.currentTimeMillis()
        val lotId = dao.addLot(LotEntity(description = description, createdAt = now, createdBy = u.id))
        val lot = LotEntity(lotId, "L-%03d".format(lotId), description, now, u.id)
        dao.updateLot(lot)
        val base = priceTotal / quantity
        val rem = priceTotal % quantity
        repeat(quantity) { i ->
            val animalId = dao.addAnimal(AnimalEntity(lotId = lotId, sex = sex,
                initialWeightKg = initialWeight, currentWeightKg = initialWeight,
                purchaseCents = base + if (i < rem) 1 else 0,
                createdAt = now, createdBy = u.id))
            dao.animal(animalId)?.let { dao.updateAnimal(it.copy(code = "CH-%03d".format(animalId),
                tagNumber = "%03d".format(animalId))) }
        }
        addAuditInternal(u.id, "CREAR", ModuleId.LOTS, "${lot.code}: $quantity animales; total ${Money.format(priceTotal)}")
    }

    suspend fun updateAnimalWeight(animalId: Long, weight: Double) = db.withTransaction {
        requireAccess(ModuleId.ANIMALS, "EDITAR")
        require(weight > 0) { "Peso inválido" }
        val a = dao.animal(animalId) ?: error("Animal no encontrado")
        require(a.state == "ACTIVO") { "El animal no está activo" }
        dao.updateAnimal(a.copy(currentWeightKg = weight))
        addAuditInternal(currentUser!!.id, "EDITAR", ModuleId.ANIMALS, "${a.code}: peso ${a.currentWeightKg} → $weight kg")
    }

    suspend fun changeTag(animalId: Long, tag: String) = db.withTransaction {
        requireAccess(ModuleId.ANIMALS, "EDITAR")
        require(tag.isNotBlank()) { "Arete vacío" }
        require(dao.animals().none { it.id != animalId && it.tagNumber == tag }) { "Arete ya utilizado" }
        val a = dao.animal(animalId) ?: error("Animal no encontrado")
        dao.updateAnimal(a.copy(tagNumber = tag))
        addAuditInternal(currentUser!!.id, "CAMBIAR_ARETE", ModuleId.ANIMALS, "${a.code}: ${a.tagNumber} → $tag")
    }

    suspend fun newBirth(motherId: Long, fatherId: Long?, born: Int, alive: Int) = db.withTransaction {
        requireAccess(ModuleId.BIRTHS, "CREAR")
        val mother = dao.animal(motherId) ?: error("Madre inexistente")
        require(mother.sex == "HEMBRA" && mother.state == "ACTIVO") { "La madre debe ser hembra activa" }
        require(born in 1..30 && alive in 0..born) { "Cantidad de crías inválida" }
        val father = fatherId?.let { dao.animal(it) ?: error("Padre no encontrado") }
        require(father == null || father.sex == "MACHO") { "El padre debe ser macho" }
        val now = System.currentTimeMillis()
        val birthId = dao.addBirth(BirthEntity(motherId = motherId, fatherId = fatherId,
            born = born, alive = alive, date = now, createdBy = currentUser!!.id))
        repeat(alive) {
            val id = dao.addAnimal(AnimalEntity(lotId = mother.lotId, origin = "NACIDO",
                motherId = motherId, fatherId = fatherId, birthId = birthId,
                createdAt = now, createdBy = currentUser!!.id))
            dao.animal(id)?.let { a -> dao.updateAnimal(a.copy(code = "CH-%03d".format(id), tagNumber = "")) }
        }
        addAuditInternal(currentUser!!.id, "PARTO", ModuleId.BIRTHS,
            "${mother.code}: $born nacidos, $alive vivos (parto $birthId)")
    }

    private suspend fun addExpenseInternal(expense: ExpenseEntity) {
        val eligible = when (expense.scope) {
            "INDIVIDUAL" -> listOfNotNull(expense.animalId?.let { dao.animal(it) }).filter { it.state == "ACTIVO" }
            "LOTE" -> dao.activeInLot(expense.lotId ?: -1)
            "GENERAL" -> dao.activeAnimals()
            "ACTIVO" -> emptyList()
            else -> error("Tipo de gasto inválido")
        }
        require(expense.scope == "ACTIVO" || eligible.isNotEmpty()) { "No hay animales activos para distribuir el gasto" }
        val expenseId = dao.addExpense(expense)
        if (eligible.isNotEmpty()) {
            val ordered = eligible.sortedBy { it.id }
            val each = expense.amountCents / ordered.size
            val remainder = expense.amountCents % ordered.size
            dao.addAllocations(ordered.mapIndexed { i, a ->
                AllocationEntity(expenseId, a.id, each + if (i < remainder) 1 else 0)
            })
        }
    }

    suspend fun newExpense(description: String, category: String, amount: Long, scope: String,
                           animalId: Long?, lotId: Long?, paidBy: String, reimbursable: Boolean) = db.withTransaction {
        requireAccess(ModuleId.EXPENSES, "CREAR")
        require(description.isNotBlank() && amount > 0) { "Descripción y monto obligatorios" }
        require(paidBy in listOf("SOCIO_1", "SOCIO_2", "CAJA")) { "Pagador inválido" }
        require(!reimbursable || paidBy != "CAJA") { "La caja del negocio no genera reembolso" }
        addExpenseInternal(ExpenseEntity(description = description, category = category,
            amountCents = amount, scope = scope, animalId = animalId, lotId = lotId,
            paidBy = paidBy, reimbursable = reimbursable,
            createdAt = System.currentTimeMillis(), createdBy = currentUser!!.id))
        addAuditInternal(currentUser!!.id, "CREAR", ModuleId.EXPENSES,
            "$description ${Money.format(amount)} pagó $paidBy; alcance $scope")
    }

    suspend fun voidExpense(id: Long, reason: String) = db.withTransaction {
        requireAccess(ModuleId.EXPENSES, "ANULAR")
        require(reason.length >= 5) { "Indica un motivo de al menos 5 caracteres" }
        val e = dao.expense(id) ?: error("No existe")
        check(!e.voided) { "Ya estaba anulado" }
        dao.updateExpense(e.copy(voided = true, voidReason = reason))
        addAuditInternal(currentUser!!.id, "ANULAR", ModuleId.EXPENSES, "Gasto $id: $reason")
    }

    suspend fun newSale(animalId: Long, amount: Long, weight: Double, buyer: String) = db.withTransaction {
        requireAccess(ModuleId.SALES, "CREAR")
        val a = dao.animal(animalId) ?: error("Animal inexistente")
        check(a.state == "ACTIVO") { "El animal no está disponible" }
        require(amount > 0 && weight > 0) { "Monto y peso deben ser positivos" }
        dao.addSale(SaleEntity(animalId = animalId, amountCents = amount,
            weightKg = weight, buyer = buyer, date = System.currentTimeMillis(), createdBy = currentUser!!.id))
        dao.updateAnimal(a.copy(state = "VENDIDO", currentWeightKg = weight))
        addAuditInternal(currentUser!!.id, "VENDER", ModuleId.SALES, "${a.code}: ${Money.format(amount)}; $weight kg")
    }

    suspend fun setPermission(role: String, module: String, op: String, value: Boolean) = db.withTransaction {
        requireAccess(ModuleId.USERS, "EDITAR")
        require(currentUser!!.roleId == RoleId.PRINCIPAL) { "Solo el administrador principal" }
        require(role != RoleId.PRINCIPAL) { "Los permisos del principal son inmutables" }
        val current = dao.permission(role, module) ?: error("Módulo desconocido")
        val updated = when (op) {
            "VER" -> current.copy(canView = value,
                canCreate = if (value) current.canCreate else false,
                canEdit = if (value) current.canEdit else false,
                canVoid = if (value) current.canVoid else false)
            "CREAR" -> current.copy(canCreate = value, canView = current.canView || value)
            "EDITAR" -> current.copy(canEdit = value, canView = current.canView || value)
            "ANULAR" -> current.copy(canVoid = value, canView = current.canView || value)
            else -> error("Operación no reconocida")
        }
        dao.savePermissions(updated)
        addAuditInternal(currentUser!!.id, "PERMISO", ModuleId.USERS, "$role $module $op=$value")
    }

    suspend fun blockUser(userId: String, enabled: Boolean) = db.withTransaction {
        requireAccess(ModuleId.USERS, "EDITAR")
        require(currentUser!!.roleId == RoleId.PRINCIPAL && userId != "U001") { "No se puede bloquear al principal" }
        val old = dao.user(userId) ?: error("Usuario no encontrado")
        dao.saveUsers(old.copy(enabled = enabled))
        addAuditInternal(currentUser!!.id, "CUENTA", ModuleId.USERS, "$userId habilitado=$enabled")
    }

    suspend fun setFlag(key: String, value: Boolean) = db.withTransaction {
        requireAccess(ModuleId.SETTINGS, "EDITAR")
        require(currentUser!!.roleId == RoleId.PRINCIPAL) { "Solo administrador principal" }
        require(key in listOf("ALLOW_LOGIN", "MAINTENANCE")) { "La auditoría es obligatoria" }
        dao.saveSettings(SettingEntity(key, value.toString()))
        addAuditInternal(currentUser!!.id, "FLAG", ModuleId.SETTINGS, "$key=$value")
    }
}

data class Snapshot(
    val user: UserEntity,
    val modules: Set<String> = emptySet(),
    val users: List<UserEntity> = emptyList(),
    val userNames: Map<String, String> = emptyMap(),
    val permissions: List<PermissionEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val partners: List<PartnerEntity> = emptyList(),
    val contributions: List<ContributionEntity> = emptyList(),
    val lots: List<LotEntity> = emptyList(),
    val animals: List<AnimalEntity> = emptyList(),
    val births: List<BirthEntity> = emptyList(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val allocations: List<AllocationEntity> = emptyList(),
    val sales: List<SaleEntity> = emptyList(),
    val audits: List<AuditEntity> = emptyList()
) {
    fun cost(animalId: Long): Long {
        val validIds = expenses.filterNot { it.voided }.map { it.id }.toSet()
        return allocations.filter { it.animalId == animalId && it.expenseId in validIds }.sumOf { it.amountCents }
    }
    fun realizedProfit(): Long = sales.filterNot { it.voided }.sumOf { sale ->
        sale.amountCents - (animals.firstOrNull { it.id == sale.animalId }?.purchaseCents ?: 0L) - cost(sale.animalId)
    }
    fun paidBy(id: String): Long = expenses.filter { !it.voided && it.paidBy == id }.sumOf { it.amountCents }
    fun reimbursableBy(id: String): Long = expenses.filter { !it.voided && it.paidBy == id && it.reimbursable }.sumOf { it.amountCents }
}
