package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tarifa_pago")
data class TarifaPago(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nombre: String,
    val porcentaje: Double,
    val descripcion: String
) 