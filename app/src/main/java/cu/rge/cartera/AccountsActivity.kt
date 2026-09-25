package cu.rge.cartera

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.annotation.SuppressLint
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.databinding.ActivityAccountsBinding
import cu.rge.cartera.databinding.ItemAccountBinding
import cu.rge.cartera.navigation.DataUpdateObserver
import cu.rge.cartera.navigation.DataUpdateType
import cu.rge.cartera.navigation.DataUpdateListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Clase temporal para mostrar cuentas con saldo real
data class AccountWithBalance(
    val account: Account,
    val realBalance: Double
)

class AccountsActivity : AppCompatActivity(), DataUpdateListener {
    private lateinit var binding: ActivityAccountsBinding
    private val accountsWithBalance = mutableListOf<AccountWithBalance>()
    private lateinit var db: AppDatabase
    private lateinit var adapter: AccountAdapter
    private val dataUpdateObserver = DataUpdateObserver.getInstance()
    
    // Interfaz para manejar clics en los elementos de la lista
    private val onAccountClick: (Account) -> Unit = { account ->
        // Mostrar diálogo de edición al hacer clic en una cuenta
        showAddOrEditAccountDialog(account)
    }
    
    // Interfaz para manejar clics en el botón de eliminar
    private val onDeleteClick: (Account) -> Unit = { account ->
        // Mostrar diálogo de confirmación al hacer clic en eliminar
        showDeleteAccountDialog(account)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAccountsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(this)
        
        setupRecyclerView()
        setupFab()
        loadAccountsWithBalance()
    }
    
    override fun onResume() {
        super.onResume()
        dataUpdateObserver.addObserver(this)
    }
    
    override fun onPause() {
        super.onPause()
        dataUpdateObserver.removeObserver(this)
    }
    
    @SuppressLint("SetTextI18n")
    override fun onDataChanged(updateType: DataUpdateType) {
        when (updateType) {
            DataUpdateType.TRANSACTIONS, 
            DataUpdateType.ACCOUNTS -> {
                loadAccountsWithBalance()
            }
            else -> {}
        }
    }
    
    private fun setupRecyclerView() {
        binding.recyclerViewAccounts.layoutManager = LinearLayoutManager(this)
        adapter = AccountAdapter(
            accounts = accountsWithBalance,
            onItemClick = onAccountClick,
            onDeleteClick = onDeleteClick
        )
        binding.recyclerViewAccounts.adapter = adapter
    }
    
    private fun getDefaultCurrency(): String {
        // Aquí deberías obtener la moneda por defecto de las preferencias
        return "CUP" // Valor por defecto
    }
    
    private fun setupFab() {
        binding.fabAgregarCuenta.setOnClickListener {
            showAddOrEditAccountDialog(null)
        }
    }

    private fun loadAccountsWithBalance() {
        lifecycleScope.launch {
            try {
                val accounts = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                accountsWithBalance.clear()
                var totalBalance = 0.0
                
                accounts.forEach { account ->
                    val balance = withContext(Dispatchers.IO) {
                        db.transactionDao().getTotalBalanceByAccount(account.id) ?: 0.0
                    }
                    accountsWithBalance.add(AccountWithBalance(account, balance))
                    totalBalance += balance
                }
                
                // Update UI on main thread
                withContext(Dispatchers.Main) {
                    binding.textViewTotalBalance.text = String.format("%.2f CUP", totalBalance)
                    adapter.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                Log.e("AccountsActivity", "Error al cargar cuentas", e)
            }
        }
    }

    
    private fun saveAccount(account: Account) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (account.id == 0L) {
                        db.accountDao().insert(account)
                    } else {
                        db.accountDao().update(account)
                    }
                }
                // Notificar el cambio
                DataUpdateObserver.getInstance().notifyDataChanged(DataUpdateType.ACCOUNTS)
                loadAccountsWithBalance()
                Toast.makeText(this@AccountsActivity, "Cuenta guardada", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("AccountsActivity", "Error al guardar cuenta", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AccountsActivity, "Error al guardar la cuenta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun showAddOrEditAccountDialog(account: Account?) {
        val dialogView = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, null)
        val inputName = EditText(this)
        inputName.hint = "Nombre de la cuenta"
        val inputType = EditText(this)
        inputType.hint = "Tipo de cuenta (Ej: Efectivo, Banco, etc.)"
        val inputBalance = EditText(this)
        inputBalance.hint = "Saldo inicial"
        inputBalance.inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        val inputCurrency = Spinner(this)
        val currencies = listOf("CUP", "USD", "EUR", "MLC")
        val adapterCurrency = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapterCurrency.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        inputCurrency.adapter = adapterCurrency

        val layout = android.widget.LinearLayout(this)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 0)
        layout.addView(inputName)
        layout.addView(inputType)
        layout.addView(inputBalance)
        layout.addView(inputCurrency)

        if (account != null) {
            inputName.setText(account.name)
            inputType.setText(account.type)
            inputBalance.setText(account.initialBalance.toString())
            inputCurrency.setSelection(currencies.indexOf(account.currency))
        }

        AlertDialog.Builder(this)
            .setTitle(if (account == null) "Nueva cuenta" else "Editar cuenta")
            .setView(layout)
            .setPositiveButton(if (account == null) "Crear" else "Guardar") { _, _ ->
                val name = inputName.text.toString()
                val type = inputType.text.toString()
                val balance = inputBalance.text.toString().toDoubleOrNull() ?: 0.0
                val currency = inputCurrency.selectedItem?.toString() ?: "CUP"
                if (name.isBlank() || type.isBlank()) {
                    Toast.makeText(this, "Completa todos los campos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    if (account == null) {
                        val newAccount = Account(name = name, type = type, initialBalance = balance, currency = currency, exchangeRate = 1.0)
                        withContext(Dispatchers.IO) { db.accountDao().insert(newAccount) }
                    } else {
                        val updated = account.copy(name = name, type = type, initialBalance = balance, currency = currency)
                        withContext(Dispatchers.IO) { db.accountDao().update(updated) }
                    }
                    loadAccountsWithBalance()
                    // Notificar cambios en cuentas
                    dataUpdateObserver.notifyAccountsChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showDeleteAccountDialog(account: Account) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar cuenta")
            .setMessage("¿Seguro que deseas eliminar la cuenta '${account.name}'?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.accountDao().delete(account) }
                    loadAccountsWithBalance()
                    // Notificar cambios en cuentas
                    dataUpdateObserver.notifyDataChanged(DataUpdateType.ACCOUNTS)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

}

class AccountAdapter(
    private val accounts: List<AccountWithBalance>,
    private val onItemClick: (Account) -> Unit,
    private val onDeleteClick: (Account) -> Unit
) : RecyclerView.Adapter<AccountAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAccountBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = accounts[position]
        holder.binding.apply {
            textViewAccountName.text = item.account.name
            textViewAccountBalance.text = String.format("%.2f %s", item.realBalance, item.account.currency)
            root.setOnClickListener { onItemClick(item.account) }
            buttonDeleteAccount.setOnClickListener {
                onDeleteClick(item.account)
            }
        }
    }

    override fun getItemCount() = accounts.size

    class ViewHolder(val binding: ItemAccountBinding) : RecyclerView.ViewHolder(binding.root)
}