package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "abonos_persona")
data class AbonoPersona(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personaId: Long,
    val monto: Double,
    val moneda: String,
    val fecha: Date = Date(),
    val nota: String? = null,
    val transactionId: Long? = null
) 