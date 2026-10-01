package dz.iline.tashilmedical
import android.content.Context
import androidx.room.*
import java.security.MessageDigest

@Entity data class Profile(@PrimaryKey val id: Int = 1, val uid: String, val name: String, val structure: String,
    val facility: String, val role: String, val shift: String, val etab: String, val dept: String, val onDuty: Boolean = false)
@Entity data class AlertRow(@PrimaryKey val id: String, val fromName: String, val targetRole: String, val ts: Long, val acked: Boolean = false)

@Dao interface Dao {
    @Query("SELECT * FROM Profile WHERE id=1") suspend fun profile(): Profile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(p: Profile)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun add(a: AlertRow): Long
    @Query("SELECT * FROM AlertRow ORDER BY ts DESC LIMIT 20") suspend fun alerts(): List<AlertRow>
    @Query("UPDATE AlertRow SET acked=1") suspend fun ackAll()
}

@Database(entities = [Profile::class, AlertRow::class], version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
    companion object {
        @Volatile private var i: Db? = null
        fun get(c: Context): Db = i ?: synchronized(this) {
            i ?: Room.databaseBuilder(c.applicationContext, Db::class.java, "tm.db").build().also { i = it }
        }
    }
}

/** Serials are derived automatically (salted SHA-256, 12 hex chars) — never typed by the user. */
object Serial {
    private fun h(s: String) = MessageDigest.getInstance("SHA-256").digest(("TM1|$s").toByteArray())
        .joinToString("") { "%02x".format(it) }.take(12)
    fun etab(structure: String) = h("E|$structure")
    fun dept(structure: String, facility: String) = h("D|$structure|$facility")
}

object Catalog {
    val establishments = mapOf(
        "E.P.S.P ES SENIA" to listOf("POLYCLINIQUE ES SENIA", "POLYCLINIQUE AADL AIN BEIDA MABROUK LOUCIF"),
        "E.P.H" to listOf("À compléter"), "C.H.U" to listOf("À compléter"), "E.H.U" to listOf("À compléter"))
    val roles = listOf("Médecin généraliste", "Infirmier", "Laboratoire", "Radiologie", "Chauffeur d'ambulance", "Agent de sécurité")
    val shifts = listOf("Équipe A", "Équipe B", "Équipe C", "Équipe D", "Équipe E", "Équipe F")
}
