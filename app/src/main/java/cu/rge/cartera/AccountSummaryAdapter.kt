package cu.rge.cartera

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.data.model.AccountWithBalance

class AccountSummaryAdapter(private val items: List<AccountWithBalance>) : RecyclerView.Adapter<AccountSummaryAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_account_summary, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.account.name
        holder.balance.text = "%.2f %s".format(item.realBalance, item.account.currency)
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, AccountDetailActivity::class.java)
            intent.putExtra("account_id", item.account.id)
            context.startActivity(intent)
        }
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.textViewAccountName)
        val balance: TextView = view.findViewById(R.id.textViewAccountBalance)
    }
} 