package cu.rge.cartera

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cu.rge.cartera.data.model.Transaction

class TransactionAdapter(
    private val items: List<Transaction>,
    private val onDelete: (Transaction) -> Unit,
    private val accountCurrency: String
) : RecyclerView.Adapter<TransactionAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_transaction, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.description.text = item.description
        holder.amount.text = "%.2f %s".format(item.amount, accountCurrency)
        holder.date.text = item.date.toString()
        holder.category.text = item.category
        holder.buttonDelete.setOnClickListener { onDelete(item) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val description: TextView = view.findViewById(R.id.transactionDescription)
        val amount: TextView = view.findViewById(R.id.transactionAmount)
        val date: TextView = view.findViewById(R.id.transactionDate)
        val category: TextView = view.findViewById(R.id.transactionCategory)
        val buttonDelete: ImageButton = view.findViewById(R.id.buttonDeleteTransaction)
    }
} 