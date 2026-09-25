package cu.rge.cartera.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import androidx.room.OnConflictStrategy
import cu.rge.cartera.data.model.Transaction

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    suspend fun getTransactionsByUser(userId: String): List<Transaction>

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId")
    suspend fun getTotalBalance(userId: String): Double

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = :type")
    suspend fun getTotalByType(userId: String, type: String): Double

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: Transaction): Long

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): Transaction?

    @Update
    suspend fun update(transaction: Transaction)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE category = :categoryName")
    suspend fun countByCategory(categoryName: String): Int

    @Query("DELETE FROM transactions WHERE category = :categoryName")
    suspend fun deleteByCategory(categoryName: String)

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND type = 'Ingreso'")
    suspend fun getTotalIncomeByAccount(accountId: Long?): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND type = 'Gasto'")
    suspend fun getTotalExpenseByAccount(accountId: Long?): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId")
    suspend fun getTotalBalanceByAccount(accountId: Long?): Double?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId ORDER BY date DESC")
    suspend fun getByAccount(accountId: Long?): List<Transaction>

    @Query("DELETE FROM transactions WHERE id = :transactionId")
    suspend fun deleteById(transactionId: Long)

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND type = 'Transferencia' AND amount > 0")
    suspend fun getTotalTransferInByAccount(accountId: Long?): Double?

    @Query("SELECT SUM(amount) FROM transactions WHERE accountId = :accountId AND type = 'Transferencia' AND amount < 0")
    suspend fun getTotalTransferOutByAccount(accountId: Long?): Double?

    @Query("DELETE FROM transactions WHERE type = 'Transferencia' AND date = :date AND description = :description AND category = :category")
    suspend fun deleteTransferPair(date: java.util.Date, description: String, category: String)
} 