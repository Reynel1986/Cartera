package cu.rge.cartera.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: String,
    val amount: Double,
    val description: String,
    val type: String,
    val category: String,
    val accountId: Long?,
    val date: Date
) 