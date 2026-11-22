package pe.granja.porcina.data

import androidx.room.*

object ModuleId {
    const val DASHBOARD = "MOD_001"
    const val ANIMALS = "MOD_002"
    const val LOTS = "MOD_003"
    const val BIRTHS = "MOD_004"
    const val EXPENSES = "MOD_005"
    const val SALES = "MOD_006"
    const val REPORTS = "MOD_007"
    const val PARTNERS = "MOD_008"
    const val USERS = "MOD_009"
    const val AUDIT = "MOD_010"
    const val SETTINGS = "MOD_011"
    val all = listOf(
        DASHBOARD to "Inicio", ANIMALS to "Chanchos", LOTS to "Lotes",
        BIRTHS to "Partos y crías", EXPENSES to "Gastos", SALES to "Ventas",
        REPORTS to "Rentabilidad", PARTNERS to "Socios", USERS to "Usuarios y roles",
        AUDIT to "Auditoría", SETTINGS to "Configuración"
    )
}

object RoleId {
    const val PRINCIPAL = "ROL_001"
    const val OPERATIVO = "ROL_002"
    const val VISOR = "ROL_003"
    fun label(role: String) = when (role) {
        PRINCIPAL -> "Administrador principal"
        OPERATIVO -> "Administrador operativo"
        else -> "Visualizador"
    }
}

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val name: String,
    val email: String,
    val passwordHash: String,
    val salt: String,
    val roleId: String,
    val enabled: Boolean = true
)

@Entity(tableName = "role_permissions", primaryKeys = ["roleId", "moduleId"])
data class PermissionEntity(
    val roleId: String,
    val moduleId: String,
    val canView: Boolean,
    val canCreate: Boolean,
    val canEdit: Boolean,
    val canVoid: Boolean
)

@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "partners")
data class PartnerEntity(@PrimaryKey val id: String, val name: String, val sharePercent: Int)

@Entity(tableName = "contributions")
data class ContributionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val partnerId: String,
    val amountCents: Long,
    val type: String = "CAPITAL",
    val note: String = "",
    val createdAt: Long,
    val createdBy: String
)

@Entity(tableName = "lots")
data class LotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String = "",
    val description: String = "",
    val createdAt: Long,
    val createdBy: String
)

@Entity(tableName = "animals")
data class AnimalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String = "",
    val tagNumber: String = "",
    val lotId: Long,
    val origin: String = "COMPRADO",
    val sex: String = "SIN_DEFINIR",
    val initialWeightKg: Double = 0.0,
    val currentWeightKg: Double = 0.0,
    val purchaseCents: Long = 0,
    val motherId: Long? = null,
    val fatherId: Long? = null,
    val birthId: Long? = null,
    val state: String = "ACTIVO",
    val createdAt: Long,
    val createdBy: String
)

@Entity(tableName = "births")
data class BirthEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val motherId: Long,
    val fatherId: Long? = null,
    val born: Int,
    val alive: Int,
    val date: Long,
    val createdBy: String
)

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val description: String,
    val amountCents: Long,
    val scope: String, // INDIVIDUAL / LOTE / GENERAL / ACTIVO
    val animalId: Long? = null,
    val lotId: Long? = null,
    val paidBy: String, // SOCIO_1 / SOCIO_2 / CAJA
    val reimbursable: Boolean = false,
    val voided: Boolean = false,
    val voidReason: String = "",
    val createdAt: Long,
    val createdBy: String
)

@Entity(tableName = "allocations", primaryKeys = ["expenseId", "animalId"])
data class AllocationEntity(val expenseId: Long, val animalId: Long, val amountCents: Long)

@Entity(tableName = "sales")
data class SaleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val animalId: Long,
    val amountCents: Long,
    val weightKg: Double,
    val buyer: String,
    val voided: Boolean = false,
    val voidReason: String = "",
    val date: Long,
    val createdBy: String
)

@Entity(tableName = "audits")
data class AuditEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val actorUserId: String,
    val action: String,
    val moduleId: String,
    val detail: String,
    val createdAt: Long
)

@Dao
interface GranjaDao {
    @Query("SELECT * FROM users ORDER BY id") suspend fun users(): List<UserEntity>
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1") suspend fun userByEmail(email: String): UserEntity?
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1") suspend fun user(id: String): UserEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveUsers(vararg items: UserEntity)

    @Query("SELECT * FROM role_permissions") suspend fun permissions(): List<PermissionEntity>
    @Query("SELECT * FROM role_permissions WHERE roleId = :roleId AND moduleId = :moduleId LIMIT 1")
    suspend fun permission(roleId: String, moduleId: String): PermissionEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePermissions(vararg items: PermissionEntity)

    @Query("SELECT * FROM settings WHERE `key` = :key LIMIT 1") suspend fun setting(key: String): SettingEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveSettings(vararg items: SettingEntity)

    @Query("SELECT * FROM partners ORDER BY id") suspend fun partners(): List<PartnerEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePartners(vararg items: PartnerEntity)
    @Query("SELECT * FROM contributions ORDER BY createdAt DESC") suspend fun contributions(): List<ContributionEntity>
    @Insert suspend fun addContribution(item: ContributionEntity): Long

    @Query("SELECT * FROM lots ORDER BY id DESC") suspend fun lots(): List<LotEntity>
    @Insert suspend fun addLot(item: LotEntity): Long
    @Update suspend fun updateLot(item: LotEntity)

    @Query("SELECT * FROM animals ORDER BY id DESC") suspend fun animals(): List<AnimalEntity>
    @Query("SELECT * FROM animals WHERE id = :id LIMIT 1") suspend fun animal(id: Long): AnimalEntity?
    @Query("SELECT * FROM animals WHERE lotId = :lotId AND state = 'ACTIVO'") suspend fun activeInLot(lotId: Long): List<AnimalEntity>
    @Query("SELECT * FROM animals WHERE state = 'ACTIVO'") suspend fun activeAnimals(): List<AnimalEntity>
    @Insert suspend fun addAnimal(item: AnimalEntity): Long
    @Update suspend fun updateAnimal(item: AnimalEntity)

    @Query("SELECT * FROM births ORDER BY id DESC") suspend fun births(): List<BirthEntity>
    @Insert suspend fun addBirth(item: BirthEntity): Long

    @Query("SELECT * FROM expenses ORDER BY id DESC") suspend fun expenses(): List<ExpenseEntity>
    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1") suspend fun expense(id: Long): ExpenseEntity?
    @Insert suspend fun addExpense(item: ExpenseEntity): Long
    @Update suspend fun updateExpense(item: ExpenseEntity)
    @Insert suspend fun addAllocations(items: List<AllocationEntity>)
    @Query("SELECT * FROM allocations") suspend fun allocations(): List<AllocationEntity>

    @Query("SELECT * FROM sales ORDER BY id DESC") suspend fun sales(): List<SaleEntity>
    @Insert suspend fun addSale(item: SaleEntity): Long
    @Update suspend fun updateSale(item: SaleEntity)

    @Query("SELECT * FROM audits ORDER BY id DESC LIMIT 200") suspend fun audits(): List<AuditEntity>
    @Insert suspend fun addAudit(item: AuditEntity)
}

@Database(
    entities = [UserEntity::class, PermissionEntity::class, SettingEntity::class,
        PartnerEntity::class, ContributionEntity::class, LotEntity::class, AnimalEntity::class,
        BirthEntity::class, ExpenseEntity::class, AllocationEntity::class, SaleEntity::class,
        AuditEntity::class],
    version = 1, exportSchema = false
)
abstract class GranjaDatabase : RoomDatabase() { abstract fun dao(): GranjaDao }
