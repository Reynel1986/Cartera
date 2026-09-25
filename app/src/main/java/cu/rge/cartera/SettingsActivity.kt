package cu.rge.cartera

import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import cu.rge.cartera.data.DataManager
import cu.rge.cartera.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch
import android.content.DialogInterface
import android.app.AlertDialog
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Button
import android.widget.EditText
import android.widget.AdapterView
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.CurrencyRate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var dataManager: DataManager
    private lateinit var prefs: SharedPreferences
    private lateinit var db: AppDatabase
    private lateinit var spinnerDefaultCurrency: Spinner
    private lateinit var buttonManageRates: Button
    private lateinit var textViewCurrentRates: TextView
    private var backupUri: Uri? = null
    private var restoreUri: Uri? = null

    private val createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            backupUri = uri
            lifecycleScope.launch {
                try {
                    val file = File(cacheDir, "backup.cartera.json")
                    dataManager.exportData(file)
                    contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().copyTo(output)
                    }
                    Toast.makeText(this@SettingsActivity, "Backup realizado correctamente", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Error al exportar datos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val openRestoreLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            lifecycleScope.launch {
                try {
                    val file = File(cacheDir, "restore.cartera.json")
                    contentResolver.openInputStream(uri)?.use { input ->
                        file.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // Intentar importar con detección automática de encriptación
                    try {
                        dataManager.importDataSmart(file)
                        Toast.makeText(this@SettingsActivity, "Datos restaurados correctamente", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        if (e.message?.contains("encriptado") == true) {
                            // El backup está encriptado, solicitar credenciales
                            showCredentialsDialog(file)
                        } else {
                            throw e
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Error al importar datos: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showCredentialsDialog(file: File) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_credentials, null)
        val editTextEmail = dialogView.findViewById<EditText>(R.id.editTextEmail)
        val editTextPassword = dialogView.findViewById<EditText>(R.id.editTextPassword)

        AlertDialog.Builder(this)
            .setTitle("Backup Encriptado")
            .setMessage("Este backup está encriptado. Ingresa las credenciales del usuario que creó el backup.")
            .setView(dialogView)
            .setPositiveButton("Restaurar") { _, _ ->
                val email = editTextEmail.text.toString()
                val password = editTextPassword.text.toString()
                
                if (email.isNotEmpty() && password.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            dataManager.importData(file, email, password)
                            Toast.makeText(this@SettingsActivity, "Datos restaurados correctamente", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@SettingsActivity, "Credenciales incorrectas o error al restaurar", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    Toast.makeText(this@SettingsActivity, "Ingresa email y contraseña", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager.getInstance(applicationContext)
        prefs = getSharedPreferences("settings", MODE_PRIVATE)
        db = AppDatabase.getInstance(applicationContext)
        
        spinnerDefaultCurrency = findViewById(R.id.spinnerDefaultCurrency)
        buttonManageRates = findViewById(R.id.buttonManageRates)
        textViewCurrentRates = findViewById(R.id.textViewCurrentRates)

        setupToolbar()
        loadSettings()
        setupSwitches()
        setupButtons()
        setupDefaultCurrencySpinner()
        setupManageRatesButton()
        loadCurrentRates()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun loadSettings() {
        val darkModeSwitch = findViewById<android.widget.Switch>(R.id.switchDarkMode)
        val notificationsSwitch = findViewById<android.widget.Switch>(R.id.switchNotifications)
        
        darkModeSwitch.isChecked = prefs.getBoolean("dark_mode", false)
        notificationsSwitch.isChecked = prefs.getBoolean("notifications", true)
    }

    private fun setupSwitches() {
        val darkModeSwitch = findViewById<android.widget.Switch>(R.id.switchDarkMode)
        val notificationsSwitch = findViewById<android.widget.Switch>(R.id.switchNotifications)
        
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        notificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("notifications", isChecked).apply()
        }
    }

    private fun setupButtons() {
        val exportDataButton = findViewById<Button>(R.id.buttonBackup)
        val importDataButton = findViewById<Button>(R.id.buttonRestore)
        val logoutButton = findViewById<Button>(R.id.buttonLogout)
        
        exportDataButton.setOnClickListener {
            createBackupLauncher.launch("backup.cartera.json")
        }

        importDataButton.setOnClickListener {
            openRestoreLauncher.launch(arrayOf("application/json"))
        }
        
        logoutButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    dataManager.logout()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDefaultCurrencySpinner() {
        val currencies = listOf("CUP", "USD", "EUR", "MLC")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDefaultCurrency.adapter = adapter

        // Cargar moneda predeterminada guardada
        lifecycleScope.launch {
            val defaultCurrency = withContext(Dispatchers.IO) { 
                db.currencyRateDao().getDefaultCurrency() ?: "CUP" 
            }
            val position = currencies.indexOf(defaultCurrency)
            if (position >= 0) {
                spinnerDefaultCurrency.setSelection(position)
            }
        }

        spinnerDefaultCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val selectedCurrency = currencies[position]
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { 
                        db.currencyRateDao().insertSetting(cu.rge.cartera.data.model.AppSettings("default_currency", selectedCurrency))
                    }
                    loadCurrentRates()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupManageRatesButton() {
        buttonManageRates.setOnClickListener {
            showManageRatesDialog()
        }
    }

    private fun showManageRatesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_manage_rates, null)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.recyclerViewRates)
        val buttonAddRate = dialogView.findViewById<Button>(R.id.buttonAddRate)

        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val ratesAdapter = CurrencyRatesAdapter(mutableListOf()) { rate ->
            showEditRateDialog(rate)
        }
        recyclerView.adapter = ratesAdapter

        buttonAddRate.setOnClickListener {
            showAddRateDialog()
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Gestionar Tasas de Cambio")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .create()

        // Cargar tasas actuales
        lifecycleScope.launch {
            val rates = withContext(Dispatchers.IO) { db.currencyRateDao().getAllRates() }
            ratesAdapter.updateRates(rates)
        }

        dialog.show()
    }

    private fun showAddRateDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_rate, null)
        val spinnerFrom = dialogView.findViewById<Spinner>(R.id.spinnerFromCurrency)
        val spinnerTo = dialogView.findViewById<Spinner>(R.id.spinnerToCurrency)
        val editTextRate = dialogView.findViewById<EditText>(R.id.editTextRate)

        val currencies = listOf("CUP", "USD", "EUR", "MLC")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter

        AlertDialog.Builder(this)
            .setTitle("Nueva Tasa de Cambio")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val fromCurrency = spinnerFrom.selectedItem.toString()
                val toCurrency = spinnerTo.selectedItem.toString()
                val rate = editTextRate.text.toString().toDoubleOrNull()

                if (rate != null && rate > 0) {
                    lifecycleScope.launch {
                        val currencyRate = CurrencyRate(
                            fromCurrency = fromCurrency,
                            toCurrency = toCurrency,
                            rate = rate
                        )
                        withContext(Dispatchers.IO) { db.currencyRateDao().insertRate(currencyRate) }
                        loadCurrentRates()
                    }
                } else {
                    Toast.makeText(this, "Tasa inválida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showEditRateDialog(rate: CurrencyRate) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_rate, null)
        val spinnerFrom = dialogView.findViewById<Spinner>(R.id.spinnerFromCurrency)
        val spinnerTo = dialogView.findViewById<Spinner>(R.id.spinnerToCurrency)
        val editTextRate = dialogView.findViewById<EditText>(R.id.editTextRate)

        val currencies = listOf("CUP", "USD", "EUR", "MLC")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFrom.adapter = adapter
        spinnerTo.adapter = adapter

        // Pre-llenar con valores actuales
        spinnerFrom.setSelection(currencies.indexOf(rate.fromCurrency))
        spinnerTo.setSelection(currencies.indexOf(rate.toCurrency))
        editTextRate.setText(rate.rate.toString())

        AlertDialog.Builder(this)
            .setTitle("Editar Tasa de Cambio")
            .setView(dialogView)
            .setPositiveButton("Actualizar") { _, _ ->
                val fromCurrency = spinnerFrom.selectedItem.toString()
                val toCurrency = spinnerTo.selectedItem.toString()
                val newRate = editTextRate.text.toString().toDoubleOrNull()

                if (newRate != null && newRate > 0) {
                    lifecycleScope.launch {
                        val updatedRate = rate.copy(
                            fromCurrency = fromCurrency,
                            toCurrency = toCurrency,
                            rate = newRate
                        )
                        withContext(Dispatchers.IO) { db.currencyRateDao().updateRate(updatedRate) }
                        loadCurrentRates()
                    }
                } else {
                    Toast.makeText(this, "Tasa inválida", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .setNeutralButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { db.currencyRateDao().deleteRate(rate) }
                    loadCurrentRates()
                }
            }
            .show()
    }

    private fun loadCurrentRates() {
        lifecycleScope.launch {
            val rates = withContext(Dispatchers.IO) { db.currencyRateDao().getAllRates() }
            val defaultCurrency = withContext(Dispatchers.IO) { 
                db.currencyRateDao().getDefaultCurrency() ?: "CUP" 
            }
            
            val ratesText = buildString {
                appendLine("Moneda predeterminada: $defaultCurrency")
                appendLine()
                appendLine("Tasas de cambio configuradas:")
                if (rates.isEmpty()) {
                    appendLine("No hay tasas configuradas")
                } else {
                    rates.forEach { rate ->
                        appendLine("${rate.fromCurrency} → ${rate.toCurrency}: ${rate.rate}")
                    }
                }
            }
            
            textViewCurrentRates.text = ratesText
        }
    }
}

class CurrencyRatesAdapter(
    private val rates: MutableList<CurrencyRate>,
    private val onRateClick: (CurrencyRate) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<CurrencyRatesAdapter.ViewHolder>() {

    fun updateRates(newRates: List<CurrencyRate>) {
        rates.clear()
        rates.addAll(newRates)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rate = rates[position]
        holder.text1.text = "${rate.fromCurrency} → ${rate.toCurrency}"
        holder.text2.text = "Tasa: ${rate.rate}"
        holder.itemView.setOnClickListener { onRateClick(rate) }
    }

    override fun getItemCount() = rates.size

    class ViewHolder(view: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }
} 