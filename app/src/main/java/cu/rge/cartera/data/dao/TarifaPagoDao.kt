package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.TarifaPago

@Dao
interface TarifaPagoDao {
    @Insert
    suspend fun insert(tarifa: TarifaPago): Long

    @Update
    suspend fun update(tarifa: TarifaPago)

    @Delete
    suspend fun delete(tarifa: TarifaPago)

    @Query("SELECT * FROM tarifa_pago ORDER BY nombre ASC")
    suspend fun getAll(): List<TarifaPago>

    @Query("SELECT * FROM tarifa_pago WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TarifaPago?
} 