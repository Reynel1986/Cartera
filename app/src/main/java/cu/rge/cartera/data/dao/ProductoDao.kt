package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.Producto

@Dao
interface ProductoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(producto: Producto): Long

    @Update
    suspend fun update(producto: Producto)

    @Delete
    suspend fun delete(producto: Producto)

    @Query("SELECT * FROM productos")
    suspend fun getAll(): List<Producto>

    @Query("SELECT * FROM productos WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Producto?
} 