package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "cortes_producto")
data class CorteProducto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productoId: Long,
    val fechaCorte: Date,
    val cantidadTotalComprada: Double, // Cantidad total comprada en este ciclo
    val purchasePrice: Double, // Precio de compra al momento del corte
    val sellingPrice: Double, // Precio de venta al momento del corte
    val precioVentaDefinido: Double, // Precio planeado de venta al momento del corte
    val currency: String, // Moneda del producto
    val unit: String // Unidad del producto
)
