package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.VentaProducto

@Dao
interface VentaProductoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(venta: VentaProducto): Long

    @Update
    suspend fun update(venta: VentaProducto)

    @Delete
    suspend fun delete(venta: VentaProducto)

    @Query("SELECT * FROM ventas_producto WHERE productoId = :productoId ORDER BY fecha DESC")
    suspend fun getByProducto(productoId: Long): List<VentaProducto>

    @Query("SELECT * FROM ventas_producto ORDER BY fecha DESC")
    suspend fun getAll(): List<VentaProducto>

    @Query("SELECT * FROM ventas_producto WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VentaProducto?

    @Query("DELETE FROM ventas_producto WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM ventas_producto WHERE productoId = :productoId")
    suspend fun deleteAllByProducto(productoId: Long)

    @Query("SELECT SUM(cantidad * precioVenta) FROM ventas_producto WHERE productoId = :productoId")
    suspend fun getTotalVentasByProducto(productoId: Long): Double?

    @Query("SELECT SUM(cantidad) FROM ventas_producto WHERE productoId = :productoId")
    suspend fun getTotalCantidadVendidaByProducto(productoId: Long): Double?

    @Query("SELECT AVG(precioVenta) FROM ventas_producto WHERE productoId = :productoId")
    suspend fun getPromedioPrecioVentaByProducto(productoId: Long): Double?

    @Query("SELECT SUM(cantidad * precioVenta) FROM ventas_producto WHERE productoId = :productoId AND fecha >= :fechaDesde")
    suspend fun getTotalVentasByProductoDesde(productoId: Long, fechaDesde: java.util.Date): Double?

    @Query("SELECT SUM(cantidad) FROM ventas_producto WHERE productoId = :productoId AND fecha >= :fechaDesde")
    suspend fun getTotalCantidadVendidaByProductoDesde(productoId: Long, fechaDesde: java.util.Date): Double?

    @Query("SELECT * FROM ventas_producto WHERE productoId = :productoId AND fecha >= :fechaDesde ORDER BY fecha DESC")
    suspend fun getByProductoDesde(productoId: Long, fechaDesde: java.util.Date): List<VentaProducto>
} 