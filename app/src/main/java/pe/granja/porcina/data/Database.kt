package pe.granja.porcina.data

import androidx.room.*

object Mods {
    const val HOME = "HOME"
    const val INVENTORY = "INVENTORY"
    const val EXPENSES = "EXPENSES"
    const val PARTNERS = "PARTNERS"
    const val REPORTS = "REPORTS"
    const val ADMIN = "ADMIN"
    val list = listOf(HOME to "Inicio", INVENTORY to "Inventario", EXPENSES to "Gastos y compras", PARTNERS to "Socios y capital", REPORTS to "Reportes", ADMIN to "Administración")
}
object Roles {
    const val OWNER = "ADMIN_PRINCIPAL"
    const val OPERATOR = "ADMIN_OPERATIVO"
    const val VIEWER = "VISUALIZADOR"
    fun label(v: String) = when(v) { OWNER -> "Administrador principal"; OPERATOR -> "Administrador operativo"; else -> "Visualizador" }
}
@Entity(tableName = "users", indices = [Index(value=["email"], unique=true)])
data class User(
    @PrimaryKey val id: String, val name: String, val email: String,
    val salt: String, val hash: String, val role: String,
    val enabled: Boolean = true, val onboardingCompleted: Boolean = false
)
@Entity(tableName="role_perms", primaryKeys=["role", "module"])
data class RolePerm(val role: String, val module: String, val view: Boolean, val create: Boolean, val edit: Boolean, val void: Boolean)
@Entity(tableName="flags")
data class Flag(@PrimaryKey val name: String, val value: Boolean)
@Entity(tableName="config_values")
data class ConfigValue(@PrimaryKey val key:String,val value:String)
@Entity(tableName="partners")
data class Partner(@PrimaryKey(autoGenerate=true) val id: Long=0, val name: String, val share: Int=50, val userId: String?=null)
@Entity(tableName="contributions")
data class Contribution(@PrimaryKey(autoGenerate=true) val id: Long=0, val partnerId: Long, val cents: Long,
    val kind: String="CAPITAL", val note: String="", val at: Long=System.currentTimeMillis(), val actor: String)
@Entity(tableName="lots")
data class Lot(@PrimaryKey(autoGenerate=true) val id: Long=0, val name: String, val at: Long, val actor: String)
@Entity(tableName="animals",indices=[Index(value=["tag"],unique=true)])
data class Animal(@PrimaryKey(autoGenerate=true) val id: Long=0,
    val code: String="", val tag: String?=null, val lotId: Long, val origin: String="COMPRADO",
    val sex: String="POR_DETERMINAR", val stage: String="CRECIMIENTO",
    val birthAt: Long?=null, val acquiredAt: Long=System.currentTimeMillis(),
    val weightKg: Double=0.0, val buyCents: Long=0,
    val motherId: Long?=null, val fatherId: Long?=null,
    val status: String="ACTIVO", val deathAt: Long?=null, val deathReason: String="", val notes: String="", val actor: String)
@Entity(tableName="tag_history", indices=[Index(value=["tag"],unique=true)])
data class TagHistory(@PrimaryKey(autoGenerate=true) val id: Long=0, val animalId: Long, val tag: String, val at: Long)
@Entity(tableName="births")
data class Birth(@PrimaryKey(autoGenerate=true) val id: Long=0, val motherId: Long, val fatherId: Long?, val total: Int,
    val living: Int, val at: Long, val actor: String)
@Entity(tableName="outlays")
data class Outlay(@PrimaryKey(autoGenerate=true) val id: Long=0,
    val title: String, val purpose: String, val category: String, val project: String,
    val cents: Long, val payerId: Long?=null, val funding: String="SOCIO",
    val lotId: Long?=null, val animalId: Long?=null, val receiptUri: String?=null,
    val at: Long, val actor: String, val voided: Boolean=false, val voidReason: String="")
@Entity(tableName="food_batches")
data class FoodBatch(@PrimaryKey(autoGenerate=true) val id: Long=0, val outlayId: Long,
    val title: String, val quantityKg: Double, val remainingKg: Double,
    val openedAt: Long?=null, val exhaustedAt: Long?=null, val createdAt: Long)
@Entity(tableName="cost_allocations", primaryKeys=["sourceType", "sourceId", "animalId"])
data class CostAllocation(val sourceType: String, val sourceId: Long, val animalId: Long, val cents: Long)
@Entity(tableName="food_usages")
data class FoodUsage(@PrimaryKey(autoGenerate=true) val id: Long=0, val foodId: Long, val qtyKg: Double,
    val lotId: Long?, val animalId: Long?, val waste: Boolean, val cents: Long, val at: Long, val actor: String)
@Entity(tableName="sales")
data class Sale(@PrimaryKey(autoGenerate=true) val id: Long=0, val animalId: Long, val cents: Long,
    val weightKg: Double, val buyer: String, val infrastructureCostCents: Long=0,
    val at: Long, val actor: String, val voided: Boolean=false)
@Entity(tableName="audit")
data class Audit(@PrimaryKey(autoGenerate=true) val id: Long=0, val actor: String, val module: String, val action: String,
    val details: String, val at: Long)
@Entity(tableName="notifications")
data class Notice(@PrimaryKey(autoGenerate=true) val id: Long=0, val text: String, val module: String,
    val entityId: Long?=null, val at: Long, val read: Boolean=false)

@Dao
interface AgroDao {
    @Query("SELECT * FROM users") suspend fun users(): List<User>
    @Query("SELECT * FROM users WHERE email=:email LIMIT 1") suspend fun userByEmail(email:String):User?
    @Query("SELECT * FROM users WHERE id=:id LIMIT 1") suspend fun userById(id:String):User?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putUser(item:User)
    @Query("SELECT * FROM role_perms") suspend fun perms():List<RolePerm>
    @Query("SELECT * FROM role_perms WHERE role=:role AND module=:module LIMIT 1") suspend fun permission(role:String,module:String):RolePerm?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putPermission(p:RolePerm)
    @Query("SELECT * FROM config_values") suspend fun configs():List<ConfigValue>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putConfig(c:ConfigValue)
    @Query("SELECT * FROM flags") suspend fun flags():List<Flag>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putFlag(f:Flag)
    @Query("SELECT * FROM partners ORDER BY id") suspend fun partners():List<Partner>
    @Insert suspend fun addPartner(p:Partner):Long
    @Update suspend fun updatePartner(p:Partner)
    @Query("SELECT * FROM contributions ORDER BY at DESC") suspend fun contributions():List<Contribution>
    @Insert suspend fun addContribution(c:Contribution):Long
    @Query("SELECT * FROM lots ORDER BY id DESC") suspend fun lots():List<Lot>
    @Insert suspend fun addLot(l:Lot):Long
    @Query("SELECT * FROM animals ORDER BY id DESC") suspend fun animals():List<Animal>
    @Query("SELECT * FROM animals WHERE id=:id LIMIT 1") suspend fun animal(id:Long):Animal?
    @Query("SELECT * FROM animals WHERE lotId=:lot AND status='ACTIVO'") suspend fun activeLot(lot:Long):List<Animal>
    @Query("SELECT * FROM animals WHERE status='ACTIVO'") suspend fun activeAnimals():List<Animal>
    @Insert suspend fun addAnimal(a:Animal):Long
    @Update suspend fun updateAnimal(a:Animal)
    @Query("SELECT * FROM tag_history") suspend fun tags():List<TagHistory>
    @Insert suspend fun addTag(t:TagHistory):Long
    @Query("SELECT * FROM births ORDER BY at DESC") suspend fun births():List<Birth>
    @Insert suspend fun addBirth(b:Birth):Long
    @Query("SELECT * FROM outlays ORDER BY at DESC") suspend fun outlays():List<Outlay>
    @Insert suspend fun addOutlay(o:Outlay):Long
    @Update suspend fun updateOutlay(o:Outlay)
    @Query("SELECT * FROM food_batches ORDER BY id DESC") suspend fun foods():List<FoodBatch>
    @Query("SELECT * FROM food_batches WHERE id=:id LIMIT 1") suspend fun food(id:Long):FoodBatch?
    @Insert suspend fun addFood(f:FoodBatch):Long
    @Update suspend fun updateFood(f:FoodBatch)
    @Query("SELECT * FROM food_usages ORDER BY at DESC") suspend fun usages():List<FoodUsage>
    @Insert suspend fun addUsage(u:FoodUsage):Long
    @Query("SELECT * FROM cost_allocations") suspend fun allocations():List<CostAllocation>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun putAllocations(items:List<CostAllocation>)
    @Query("SELECT * FROM sales ORDER BY at DESC") suspend fun sales():List<Sale>
    @Insert suspend fun addSale(s:Sale):Long
    @Query("SELECT * FROM audit ORDER BY at DESC LIMIT 500") suspend fun audits():List<Audit>
    @Insert suspend fun addAudit(a:Audit):Long
    @Query("SELECT * FROM notifications ORDER BY at DESC LIMIT 200") suspend fun notices():List<Notice>
    @Query("SELECT * FROM notifications WHERE id=:id LIMIT 1") suspend fun notice(id:Long):Notice?
    @Update suspend fun updateNotice(n:Notice)
    @Insert suspend fun addNotice(n:Notice):Long
}

@Database(entities=[User::class,RolePerm::class,Flag::class,Partner::class,Contribution::class,
    Lot::class,Animal::class,TagHistory::class,Birth::class,Outlay::class,FoodBatch::class,ConfigValue::class,
    CostAllocation::class,FoodUsage::class,Sale::class,Audit::class,Notice::class], version=1, exportSchema=false)
abstract class AgroDb: RoomDatabase() { abstract fun dao():AgroDao }
