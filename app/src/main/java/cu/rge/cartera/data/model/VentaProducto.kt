package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "ventas_producto")
data class VentaProducto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoId: Long,
    val cantidad: Double,
    val precioVenta: Double,
    val moneda: String,
    val nota: String? = null,
    val fecha: Date = Date(),
    val transactionId: Long? = null // ID de la transacción asociada
) 