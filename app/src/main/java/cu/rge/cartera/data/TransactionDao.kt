package cu.rge.cartera.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import cu.rge.cartera.data.model.Transaction
import java.util.Date

@Dao
interface TransactionDao {
    @Insert
    suspend fun insertTransaction(transaction: Transaction)

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    suspend fun getTransactionsByUser(userId: String): List<Transaction>

    @Query("SELECT SUM(CASE WHEN type = 'INCOME' THEN amount ELSE -amount END) FROM transactions WHERE userId = :userId")
    suspend fun getTotalBalance(userId: String): Double?

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    suspend fun getTransactionsByDateRange(userId: String, startDate: Date, endDate: Date): List<Transaction>
} 