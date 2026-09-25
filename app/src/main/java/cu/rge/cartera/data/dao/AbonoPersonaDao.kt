package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.AbonoPersona

@Dao
interface AbonoPersonaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(abono: AbonoPersona): Long

    @Update
    suspend fun update(abono: AbonoPersona)

    @Delete
    suspend fun delete(abono: AbonoPersona)

    @Query("SELECT * FROM abonos_persona WHERE personaId = :personaId ORDER BY fecha DESC")
    suspend fun getByPersona(personaId: Long): List<AbonoPersona>

    @Query("SELECT * FROM abonos_persona ORDER BY fecha DESC")
    suspend fun getAll(): List<AbonoPersona>

    @Query("SELECT COALESCE(SUM(monto), 0) FROM abonos_persona WHERE personaId = :personaId")
    suspend fun getTotalByPersonaId(personaId: Long): Double

    @Query("DELETE FROM abonos_persona WHERE personaId = :personaId")
    suspend fun deleteByPersona(personaId: Long)
} 