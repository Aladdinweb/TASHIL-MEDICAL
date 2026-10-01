package dz.iline.tashilmedical
import android.content.Context
import androidx.room.*
import java.security.MessageDigest

@Entity data class Profile(@PrimaryKey val id: Int = 1, val uid: String, val name: String, val wilaya: String, val type: String,
    val parent: String, val facility: String, val role: String, val shift: String, val etab: String, val dept: String,
    val onDuty: Boolean = false, val dutyEnd: Long = 0)
@Entity data class AlertRow(@PrimaryKey val id: String, val fromName: String, val targetRole: String, val ts: Long,
    val sos: Boolean = false, val acked: Boolean = false)

@Dao interface TmDao {
    @Query("SELECT * FROM Profile WHERE id=1") suspend fun profile(): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: Profile)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(a: AlertRow): Long
    @Query("SELECT * FROM AlertRow ORDER BY ts DESC LIMIT 20") suspend fun alerts(): List<AlertRow>
    @Query("UPDATE AlertRow SET acked=1") suspend fun ackAll()
}

@Database(entities = [Profile::class, AlertRow::class], version = 2, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): TmDao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context): Db = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "tm.db")
                .fallbackToDestructiveMigration().build().also { i = it }
        }
    }
}

object Serial {
    private fun h(s: String) = MessageDigest.getInstance("SHA-256").digest(("TM1|$s").toByteArray())
        .joinToString("") { "%02x".format(it) }.take(12)
    fun etab(w: String, parent: String) = h("E|$w|$parent")
    fun dept(w: String, parent: String, facility: String) = h("D|$w|$parent|$facility")
}

object Catalog {
    const val SEC = "Agent de sécurité"
    private val todo = listOf("À compléter")
    // wilaya -> type -> parent institution -> branches
    val tree: Map<String, Map<String, Map<String, List<String>>>> = mapOf(
        "Oran" to mapOf(
            "EPSP" to mapOf(
                "EPSP ES SENIA" to listOf("POLYCLINIQUE ES SENIA", "POLYCLINIQUE AADL AIN BEIDA MABROUK LOUCIF",
                    "POLYCLINIQUE AIN BEIDA 1", "POLYCLINIQUE AIN BEIDA 2", "POLYCLINIQUE SIDI MAAROUF", "POLYCLINIQUE SIDI CHAHMI"),
                "EPSP MISSÉRGHIN" to todo, "EPSP SEDDIKIA" to todo, "EPSP AÏN EL TRUCK" to todo, "EPSP FRONT DE MER" to todo),
            "EPH" to mapOf("À compléter" to todo), "CHU" to mapOf("À compléter" to todo), "EHU" to mapOf("À compléter" to todo)),
        "Autre wilaya" to mapOf("EPSP" to mapOf("À compléter" to todo)))
    val roles = listOf("Médecin généraliste", "Infirmier", "Laboratoire", "Radiologie", "Chauffeur d'ambulance", SEC)
    val shifts = listOf("Équipe A", "Équipe B", "Équipe C", "Équipe D", "Équipe E", "Équipe F")
}
