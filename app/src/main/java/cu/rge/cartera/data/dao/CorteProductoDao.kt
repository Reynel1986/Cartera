package cu.rge.cartera.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cu.rge.cartera.data.model.CorteProducto
import java.util.Date

@Dao
interface CorteProductoDao {
    @Insert
    suspend fun insert(corte: CorteProducto): Long

    @Query("SELECT * FROM cortes_producto WHERE productoId = :productoId ORDER BY fechaCorte DESC")
    suspend fun getByProducto(productoId: Long): List<CorteProducto>

    @Query("SELECT * FROM cortes_producto WHERE productoId = :productoId AND fechaCorte = (SELECT MAX(fechaCorte) FROM cortes_producto WHERE productoId = :productoId) LIMIT 1")
    suspend fun getUltimoCorte(productoId: Long): CorteProducto?

    @Query("DELETE FROM cortes_producto WHERE id = :id")
    suspend fun deleteById(id: Long)
}
