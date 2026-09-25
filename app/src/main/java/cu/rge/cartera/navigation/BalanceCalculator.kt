package cu.rge.cartera.navigation

import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.data.model.AccountWithBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Calculadora de balance que maneja la lógica de cálculo de saldos
 * de forma centralizada y confiable
 */
class BalanceCalculator {
    
    companion object {
        @Volatile
        private var INSTANCE: BalanceCalculator? = null
        
        fun getInstance(): BalanceCalculator {
            return INSTANCE ?: synchronized(this) {
                val instance = BalanceCalculator()
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Calcula el balance total de todas las cuentas
     * @param filterByMonth Si es true, filtra las transacciones por el mes actual
     */
    suspend fun calculateTotalBalance(db: AppDatabase, defaultCurrency: String = "CUP", filterByMonth: Boolean = true): BalanceResult {
        return withContext(Dispatchers.IO) {
            val accounts = db.accountDao().getAll()
            val accountsWithBalance = mutableListOf<AccountWithBalance>()
            var totalBalance = 0.0
            var totalIncome = 0.0
            var totalExpense = 0.0
            var totalCuentas = 0.0

            // Calcular inicio y fin del mes actual si se filtra por mes
            val (startOfMonth, endOfMonth) = if (filterByMonth) {
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.time

                // Fin del mes: último día del mes a las 23:59:59.999
                calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH))
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                val end = calendar.time

                android.util.Log.d("BalanceCalculator", "Inicio mes: $start")
                android.util.Log.d("BalanceCalculator", "Fin mes: $end")

                Pair(start, end)
            } else {
                Pair(Date(0), Date()) // Todo el historial
            }

            for (account in accounts) {
                val transactions = db.transactionDao().getByAccount(account.id)

                // Filtrar transacciones por mes si está activado
                val filteredTransactions = if (filterByMonth) {
                    transactions.filter { it.date >= startOfMonth && it.date <= endOfMonth }
                } else {
                    transactions
                }

                val income = filteredTransactions.filter { it.type == "Ingreso" }.sumOf { it.amount }
                val expense = filteredTransactions.filter { it.type == "Gasto" }.sumOf { it.amount }
                val transferIn = filteredTransactions.filter { it.type == "Transferencia" && it.amount > 0 }.sumOf { it.amount }
                val transferOut = filteredTransactions.filter { it.type == "Transferencia" && it.amount < 0 }.sumOf { it.amount }

                // Para el balance total de la cuenta, siempre usar todas las transacciones
                val allTransactions = transactions
                val allIncome = allTransactions.filter { it.type == "Ingreso" }.sumOf { it.amount }
                val allExpense = allTransactions.filter { it.type == "Gasto" }.sumOf { it.amount }
                val allTransferIn = allTransactions.filter { it.type == "Transferencia" && it.amount > 0 }.sumOf { it.amount }
                val allTransferOut = allTransactions.filter { it.type == "Transferencia" && it.amount < 0 }.sumOf { it.amount }

                val realBalance = account.initialBalance + allIncome + allExpense + allTransferIn + allTransferOut

                val rate = if (account.currency == defaultCurrency) 1.0 else {
                    db.currencyRateDao().getSmartRate(account.currency, defaultCurrency) ?: 1.0
                }

                android.util.Log.d("BalanceCalculator", "Cuenta: ${account.name} (${account.currency})")
                android.util.Log.d("BalanceCalculator", "  Balance inicial: ${account.initialBalance}")
                android.util.Log.d("BalanceCalculator", "  AllIncome: $allIncome")
                android.util.Log.d("BalanceCalculator", "  AllExpense: $allExpense")
                android.util.Log.d("BalanceCalculator", "  AllTransferIn: $allTransferIn")
                android.util.Log.d("BalanceCalculator", "  AllTransferOut: $allTransferOut")
                android.util.Log.d("BalanceCalculator", "  RealBalance: $realBalance")
                android.util.Log.d("BalanceCalculator", "  Rate ($account.currency -> $defaultCurrency): $rate")

                val convertedBalance = realBalance * rate
                val convertedIncome = income * rate
                val convertedExpense = expense * rate

                android.util.Log.d("BalanceCalculator", "  ConvertedBalance: $convertedBalance")
                android.util.Log.d("BalanceCalculator", "  ConvertedIncome: $convertedIncome")
                android.util.Log.d("BalanceCalculator", "  ConvertedExpense: $convertedExpense")

                accountsWithBalance.add(AccountWithBalance(account, realBalance))
                totalBalance += convertedBalance
                totalIncome += convertedIncome
                totalExpense += convertedExpense
                totalCuentas += convertedBalance
            }

            BalanceResult(
                accountsWithBalance = accountsWithBalance,
                totalBalance = totalBalance,
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                totalCuentas = totalCuentas
            )
        }
    }
}

/**
 * Resultado del cálculo de balance
 */
data class BalanceResult(
    val accountsWithBalance: List<AccountWithBalance>,
    val totalBalance: Double,
    val totalIncome: Double,
    val totalExpense: Double,
    val totalCuentas: Double
)
