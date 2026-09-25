package cu.rge.cartera

import android.os.Bundle
import android.widget.TextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Transaction
import androidx.lifecycle.lifecycleScope

class ReportsActivity : AppCompatActivity() {
    private lateinit var textViewMonthYear: TextView
    private lateinit var buttonPrevMonth: Button
    private lateinit var buttonNextMonth: Button
    private lateinit var textViewTotals: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TransactionsAdapter
    private val transactions = mutableListOf<Transaction>()
    private var currentMonth: Int = 0
    private var currentYear: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reports)

        textViewMonthYear = findViewById(R.id.textViewMonthYear)
        buttonPrevMonth = findViewById(R.id.buttonPrevMonth)
        buttonNextMonth = findViewById(R.id.buttonNextMonth)
        textViewTotals = findViewById(R.id.textViewTotals)
        recyclerView = findViewById(R.id.recyclerViewTransactions)
        adapter = TransactionsAdapter(transactions)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        val calendar = Calendar.getInstance()
        currentMonth = calendar.get(Calendar.MONTH)
        currentYear = calendar.get(Calendar.YEAR)

        buttonPrevMonth.setOnClickListener {
            if (currentMonth == 0) {
                currentMonth = 11
                currentYear--
            } else {
                currentMonth--
            }
            loadReport()
        }
        buttonNextMonth.setOnClickListener {
            if (currentMonth == 11) {
                currentMonth = 0
                currentYear++
            } else {
                currentMonth++
            }
            loadReport()
        }

        loadReport()
    }

    private fun loadReport() {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.YEAR, currentYear)
        calendar.set(Calendar.MONTH, currentMonth)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val startDate = calendar.time
        calendar.add(Calendar.MONTH, 1)
        calendar.set(Calendar.DAY_OF_MONTH, 1)
        val endDate = Date(calendar.timeInMillis - 1)
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        textViewMonthYear.text = sdf.format(startDate).replaceFirstChar { it.uppercase() }

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
            val allTransactions = withContext(Dispatchers.IO) { db.transactionDao().getTransactionsByUser(userId) }
            val filtered = allTransactions.filter { it.date >= startDate && it.date <= endDate }
            transactions.clear()
            transactions.addAll(filtered)
            adapter.notifyDataSetChanged()

            val totalIncome = filtered.filter { it.type == "Ingreso" }.sumOf { it.amount }
            val totalExpense = filtered.filter { it.type == "Gasto" }.sumOf { it.amount }
            val totalTransferIn = filtered.filter { it.type == "Transferencia" && it.amount > 0 }.sumOf { it.amount }
            val totalTransferOut = filtered.filter { it.type == "Transferencia" && it.amount < 0 }.sumOf { it.amount }
            textViewTotals.text = "Ingresos: %.2f\nGastos: %.2f\nTransferencias recibidas: %.2f\nTransferencias enviadas: %.2f".format(totalIncome, totalExpense, totalTransferIn, totalTransferOut)
        }
    }
}

class TransactionsAdapter(private val items: List<Transaction>) : RecyclerView.Adapter<TransactionsAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text1.text = "${item.type}: ${item.amount} ${item.category}"
        holder.text2.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(item.date)
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }
} 