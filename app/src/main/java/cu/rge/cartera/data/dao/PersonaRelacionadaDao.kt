package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.PersonaRelacionada

@Dao
interface PersonaRelacionadaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(persona: PersonaRelacionada): Long

    @Update
    suspend fun update(persona: PersonaRelacionada)

    @Delete
    suspend fun delete(persona: PersonaRelacionada)

    @Query("SELECT * FROM personas_relacionadas WHERE tipo = :tipo")
    suspend fun getByTipo(tipo: String): List<PersonaRelacionada>

    @Query("SELECT * FROM personas_relacionadas WHERE tipo = :tipo AND activa = 1")
    suspend fun getByTipoAndActiva(tipo: String): List<PersonaRelacionada>

    @Query("SELECT * FROM personas_relacionadas WHERE nombre = :nombre AND tipo = :tipo LIMIT 1")
    suspend fun getByNombreAndTipo(nombre: String, tipo: String): PersonaRelacionada?

    @Query("SELECT * FROM personas_relacionadas")
    suspend fun getAll(): List<PersonaRelacionada>

    @Query("SELECT * FROM personas_relacionadas WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PersonaRelacionada?
} 