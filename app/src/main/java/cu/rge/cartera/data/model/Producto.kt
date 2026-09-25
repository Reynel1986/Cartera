package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "productos")
data class Producto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val currency: String,
    val quantity: Double = 0.0,
    val unit: String = "unidades",
    val purchasePrice: Double = 0.0,
    val sellingPrice: Double = 0.0,
    val precioVentaDefinido: Double = 0.0, // Precio planeado de venta
    val cantidadTotalComprada: Double = 0.0, // Cantidad total comprada históricamente
    val cuentaGananciaId: Long? = null, // ID de la cuenta donde se deposita la ganancia
    val cuentaVentaId: Long? = null // ID de la cuenta donde se deposita el costo de venta
) 