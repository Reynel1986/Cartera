package cu.rge.cartera.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import androidx.room.OnConflictStrategy
import cu.rge.cartera.data.model.Account

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts")
    suspend fun getAll(): List<Account>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: Account): Long

    @Delete
    suspend fun delete(account: Account)

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Account?

    @androidx.room.Update
    suspend fun update(account: Account)
} 