package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "personas_relacionadas")
data class PersonaRelacionada(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val monto: Double,
    val moneda: String,
    val tipo: String, // "DEUDOR" o "ACREEDOR"
    val descripcion: String? = null,
    val fecha: Date? = null,
    val activa: Boolean = true, // true = visible, false = oculta pero mantiene historial
    val accountId: Long? = null // Cuenta de donde se descuenta o a donde se manda el dinero
) 