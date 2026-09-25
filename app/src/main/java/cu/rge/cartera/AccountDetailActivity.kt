package cu.rge.cartera

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.LinearLayout
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import android.app.DatePickerDialog
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.Locale
import java.util.Calendar

class AccountDetailActivity : AppCompatActivity() {
    private var accountId: Long = -1
    private lateinit var account: Account
    private val transactions = mutableListOf<Transaction>()
    private lateinit var adapter: TransactionAdapter

    private lateinit var recyclerViewTransactions: RecyclerView
    private lateinit var buttonAddIncome: Button
    private lateinit var buttonAddExpense: Button
    private lateinit var buttonTransfer: Button
    private lateinit var textViewAccountName: TextView
    private lateinit var textViewAccountBalance: TextView
    private lateinit var buttonChangeCurrency: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account_detail)

        recyclerViewTransactions = findViewById(R.id.recyclerViewTransactions)
        buttonAddIncome = findViewById(R.id.buttonAddIncome)
        buttonAddExpense = findViewById(R.id.buttonAddExpense)
        buttonTransfer = findViewById(R.id.buttonTransfer)
        textViewAccountName = findViewById(R.id.textViewAccountName)
        textViewAccountBalance = findViewById(R.id.textViewAccountBalance)
        buttonChangeCurrency = findViewById(R.id.buttonChangeCurrency)

        accountId = intent.getLongExtra("account_id", -1)
        if (accountId == -1L) {
            Toast.makeText(this, "Cuenta no encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = TransactionAdapter(transactions, onDelete = { transaction ->
            eliminarTransaccion(transaction)
        }, accountCurrency = "")
        recyclerViewTransactions.layoutManager = LinearLayoutManager(this)
        recyclerViewTransactions.adapter = adapter

        buttonAddIncome.setOnClickListener {
            mostrarDialogoTransaccion("Ingreso")
        }
        buttonAddExpense.setOnClickListener {
            mostrarDialogoTransaccion("Gasto")
        }
        buttonTransfer.setOnClickListener {
            mostrarDialogoTransferencia()
        }

        buttonChangeCurrency.setOnClickListener {
            mostrarDialogoCambioMoneda()
        }

        cargarCuentaYTransacciones()
    }

    override fun onResume() {
        super.onResume()
        cargarCuentaYTransacciones()
    }

    private fun cargarCuentaYTransacciones() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            account = withContext(Dispatchers.IO) { db.accountDao().getById(accountId) } ?: return@launch
            val trans = withContext(Dispatchers.IO) { db.transactionDao().getByAccount(accountId) }
            transactions.clear()
            transactions.addAll(trans)
            adapter = TransactionAdapter(transactions, onDelete = { transaction ->
                eliminarTransaccion(transaction)
            }, accountCurrency = account.currency)
            recyclerViewTransactions.adapter = adapter
            adapter.notifyDataSetChanged()
            textViewAccountName.text = account.name
            val totalIncome = transactions.filter { it.type == "Ingreso" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "Gasto" }.sumOf { it.amount }
            val totalTransfer = transactions.filter { it.type == "Transferencia" }.sumOf { it.amount }
            val currentBalance = account.initialBalance + totalIncome + totalExpense + totalTransfer
            textViewAccountBalance.text = "%.2f %s".format(currentBalance, account.currency)
        }
    }

    private fun mostrarDialogoTransaccion(tipo: String) {
        val db = AppDatabase.getInstance(applicationContext)
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val editTextAmount = dialogView.findViewById<EditText>(R.id.editTextAmount)
        val editTextDescription = dialogView.findViewById<EditText>(R.id.editTextDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val buttonAdd = dialogView.findViewById<Button>(R.id.buttonAddTransaction)
        val spinnerTarifa = dialogView.findViewById<Spinner>(R.id.spinnerTarifaPago)
        val textViewMontoFinal = dialogView.findViewById<TextView>(R.id.textViewMontoFinal)
        val editTextDate = dialogView.findViewById<EditText>(R.id.editTextDate)
        val buttonDatePicker = dialogView.findViewById<Button>(R.id.buttonDatePicker)
        val buttonCalculator = dialogView.findViewById<Button>(R.id.buttonCalculator)
        // Ocultar selección de cuenta, ya que ya sabemos ambos
        dialogView.findViewById<Spinner>(R.id.spinnerAccount)?.visibility = android.view.View.GONE
        
        // Variable para almacenar la fecha seleccionada
        var selectedDate = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val decimalFormat = DecimalFormat("#.##")
        editTextDate.setText(dateFormat.format(selectedDate))

        lifecycleScope.launch {
            val categories = withContext(Dispatchers.IO) { db.categoryDao().getAll() }
            val adapterCategory = ArrayAdapter(this@AccountDetailActivity, android.R.layout.simple_spinner_item, categories.map { it.name })
            adapterCategory.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = adapterCategory

            // Poblar tarifas de pago
            val tarifas = withContext(Dispatchers.IO) { db.tarifaPagoDao().getAll() }
            val tarifaDefault = cu.rge.cartera.data.model.TarifaPago(nombre = "Sin tarifa", porcentaje = 0.0, descripcion = "")
            val listaTarifas = listOf(tarifaDefault) + tarifas.filter { it.porcentaje > 0.0 }
            val nombresTarifas = listaTarifas.map { "${it.nombre} (${it.porcentaje}%)" }
            val adapterTarifa = ArrayAdapter(this@AccountDetailActivity, android.R.layout.simple_spinner_item, nombresTarifas)
            adapterTarifa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTarifa.adapter = adapterTarifa
            spinnerTarifa.setSelection(0)

            // Actualizar monto final en tiempo real
            fun actualizarMontoFinal() {
                val monto = editTextAmount.text.toString().toDoubleOrNull() ?: 0.0
                val posTarifa = spinnerTarifa.selectedItemPosition
                val porcentaje = if (posTarifa > 0 && posTarifa < spinnerTarifa.adapter.count) (spinnerTarifa.adapter.getItem(posTarifa).toString().substringAfter('(').substringBefore('%').toDoubleOrNull() ?: 0.0) else 0.0
                val rebaja = monto * porcentaje / 100.0
                val montoFinal = monto - rebaja
                textViewMontoFinal.text = "Monto final: %.2f (rebaja: %.2f)".format(montoFinal, rebaja)
            }
            editTextAmount.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { actualizarMontoFinal() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            spinnerTarifa.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    actualizarMontoFinal()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

            // Configurar botón de calculadora
            buttonCalculator.setOnClickListener {
                val calculator = CalculatorDialog(this@AccountDetailActivity)
                calculator.show { result ->
                    editTextAmount.setText(decimalFormat.format(result))
                    actualizarMontoFinal()
                }
            }

            // Configurar botón de fecha
            buttonDatePicker.setOnClickListener {
                val calendar = Calendar.getInstance()
                calendar.time = selectedDate
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val datePickerDialog = DatePickerDialog(
                    this@AccountDetailActivity,
                    { _, selectedYear, selectedMonth, selectedDay ->
                        calendar.set(selectedYear, selectedMonth, selectedDay)
                        selectedDate = calendar.time
                        editTextDate.setText(dateFormat.format(selectedDate))
                    },
                    year, month, day
                )
                datePickerDialog.show()
            }

            // Configurar click en editTextDate para abrir el calendario
            editTextDate.setOnClickListener {
                buttonDatePicker.performClick()
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Nuevo $tipo")
            .setView(dialogView)
            .create()

        buttonAdd.setOnClickListener {
            val amountStr = editTextAmount.text.toString()
            val description = editTextDescription.text.toString()
            val categoryName = spinnerCategory.selectedItem.toString()
            var amount = amountStr.toDoubleOrNull()
            val posTarifa = spinnerTarifa.selectedItemPosition
            val porcentaje = if (posTarifa > 0 && posTarifa < spinnerTarifa.adapter.count) (spinnerTarifa.adapter.getItem(posTarifa).toString().substringAfter('(').substringBefore('%').toDoubleOrNull() ?: 0.0) else 0.0
            val rebaja = (amount ?: 0.0) * porcentaje / 100.0
            val montoFinal = (amount ?: 0.0) - rebaja

            if (amount == null || amount <= 0) {
                Toast.makeText(this, "Monto inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Asegurar que el gasto sea negativo
            if (tipo == "Gasto") {
                amount = -kotlin.math.abs(montoFinal)
            } else {
                amount = montoFinal
            }

            lifecycleScope.launch {
                try {
                    val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                    if (userId.isNullOrEmpty()) {
                        Toast.makeText(this@AccountDetailActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@AccountDetailActivity, LoginActivity::class.java))
                        finish()
                        return@launch
                    }
                    val transaction = Transaction(
                        userId = userId,
                        amount = amount,
                        description = description,
                        type = tipo,
                        category = categoryName,
                        accountId = accountId,
                        date = selectedDate
                    )
                    withContext(Dispatchers.IO) { db.transactionDao().insert(transaction) }
                    cargarCuentaYTransacciones()
                    dialog.dismiss()
                } catch (e: Exception) {
                    if (e.message?.contains("No hay usuario actual") == true) {
                        Toast.makeText(this@AccountDetailActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                        startActivity(Intent(this@AccountDetailActivity, LoginActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@AccountDetailActivity, "Error al guardar la transacción: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun mostrarDialogoTransferencia() {
        val db = AppDatabase.getInstance(applicationContext)
        val dialogView = layoutInflater.inflate(R.layout.dialog_transfer, null)
        val spinnerDestino = dialogView.findViewById<Spinner>(R.id.spinnerDestino)
        val editTextMonto = dialogView.findViewById<EditText>(R.id.editTextMonto)
        val editTextDescripcion = dialogView.findViewById<EditText>(R.id.editTextDescripcion)
        val spinnerTarifa = dialogView.findViewById<Spinner>(R.id.spinnerTarifaPago)
        val textViewMontoFinal = dialogView.findViewById<TextView>(R.id.textViewMontoFinal)
        val editTextDate = dialogView.findViewById<EditText>(R.id.editTextDate)
        val buttonDatePicker = dialogView.findViewById<Button>(R.id.buttonDatePicker)
        val buttonCalculator = dialogView.findViewById<Button>(R.id.buttonCalculator)
        val textViewConversion = TextView(this)
        textViewConversion.setPadding(0, 16, 0, 0)
        (dialogView as? LinearLayout)?.addView(textViewConversion)
        
        // Variable para almacenar la fecha seleccionada
        var selectedDate = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val decimalFormat = DecimalFormat("#.##")
        editTextDate.setText(dateFormat.format(selectedDate))
        // Cargar cuentas destino
        lifecycleScope.launch {
            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val cuentasDestino = cuentas.filter { it.id != accountId }
            val adapter = ArrayAdapter(this@AccountDetailActivity, android.R.layout.simple_spinner_item, cuentasDestino.map { it.name })
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerDestino.adapter = adapter
        }
        // Cargar tarifas de pago
        lifecycleScope.launch {
            val tarifas = withContext(Dispatchers.IO) { db.tarifaPagoDao().getAll() }
            val tarifaDefault = cu.rge.cartera.data.model.TarifaPago(nombre = "Sin tarifa", porcentaje = 0.0, descripcion = "")
            val listaTarifas = listOf(tarifaDefault) + tarifas.filter { it.porcentaje > 0.0 }
            val nombresTarifas = listaTarifas.map { "${it.nombre} (${it.porcentaje}%)" }
            val adapterTarifa = ArrayAdapter(this@AccountDetailActivity, android.R.layout.simple_spinner_item, nombresTarifas)
            adapterTarifa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTarifa.adapter = adapterTarifa
            spinnerTarifa.setSelection(0) // Por defecto 'Sin tarifa (0%)'
            // Actualizar monto final en tiempo real
            fun actualizarMontoFinal() {
                val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                val posTarifa = spinnerTarifa.selectedItemPosition
                val porcentaje = if (posTarifa > 0 && posTarifa in listaTarifas.indices) listaTarifas[posTarifa].porcentaje else 0.0
                val rebaja = monto * porcentaje / 100.0
                val montoFinal = monto - rebaja
                textViewMontoFinal.text = "Monto final: %.2f (rebaja: %.2f)".format(montoFinal, rebaja)
            }
            editTextMonto.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { actualizarMontoFinal() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            spinnerTarifa.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                    actualizarMontoFinal()
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }

            // Configurar botón de calculadora
            buttonCalculator.setOnClickListener {
                val calculator = CalculatorDialog(this@AccountDetailActivity)
                calculator.show { result ->
                    editTextMonto.setText(decimalFormat.format(result))
                    actualizarMontoFinal()
                }
            }

            // Configurar botón de fecha
            buttonDatePicker.setOnClickListener {
                val calendar = Calendar.getInstance()
                calendar.time = selectedDate
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)
                val day = calendar.get(Calendar.DAY_OF_MONTH)

                val datePickerDialog = DatePickerDialog(
                    this@AccountDetailActivity,
                    { _, selectedYear, selectedMonth, selectedDay ->
                        calendar.set(selectedYear, selectedMonth, selectedDay)
                        selectedDate = calendar.time
                        editTextDate.setText(dateFormat.format(selectedDate))
                    },
                    year, month, day
                )
                datePickerDialog.show()
            }

            // Configurar click en editTextDate para abrir el calendario
            editTextDate.setOnClickListener {
                buttonDatePicker.performClick()
            }

            // Ajustar el cálculo de porcentaje/rebaja en el botón de transferencia
            val alertDialog = AlertDialog.Builder(this@AccountDetailActivity)
                .setTitle("Transferir a otra cuenta")
                .setView(dialogView)
                .setPositiveButton("Transferir", null)
                .setNegativeButton("Cancelar", null)
                .create()
            alertDialog.setOnShowListener {
                fun actualizarConversion() {
                    val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                    val pos = spinnerDestino.selectedItemPosition
                    lifecycleScope.launch {
                        val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                        val cuentasDestino = cuentas.filter { it.id != accountId }
                        val cuentaDestino = cuentasDestino.getOrNull(pos)
                        if (cuentaDestino == null || monto <= 0.0) {
                            textViewConversion.text = ""
                            return@launch
                        }
                        val monedaOrigen = account.currency
                        val monedaDestino = cuentaDestino.currency
                        if (monedaOrigen == monedaDestino) {
                            textViewConversion.text = "Sin conversión de moneda"
                        } else {
                            val rate = withContext(Dispatchers.IO) { db.currencyRateDao().getSmartRate(monedaOrigen, monedaDestino) ?: 1.0 }
                            val montoDestino = monto * rate
                            textViewConversion.text = "Tasa: 1 $monedaOrigen = $rate $monedaDestino\nRecibirá: %.2f $monedaDestino".format(montoDestino)
                        }
                    }
                }
                spinnerDestino.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                        actualizarConversion()
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
                }
                editTextMonto.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) { actualizarConversion() }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                    val descripcion = editTextDescripcion.text.toString()
                    val pos = spinnerDestino.selectedItemPosition
                    val posTarifa = spinnerTarifa.selectedItemPosition
                    val porcentaje = if (posTarifa > 0 && posTarifa in listaTarifas.indices) listaTarifas[posTarifa].porcentaje else 0.0
                    val rebaja = monto * porcentaje / 100.0
                    val montoFinal = monto - rebaja
                    if (monto <= 0.0 || pos == -1) {
                        Toast.makeText(this@AccountDetailActivity, "Datos inválidos", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        try {
                            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                            val cuentasDestino = cuentas.filter { it.id != accountId }
                            val cuentaDestino = cuentasDestino.getOrNull(pos) ?: return@launch
                            val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                            if (userId.isNullOrEmpty()) {
                                Toast.makeText(this@AccountDetailActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this@AccountDetailActivity, LoginActivity::class.java))
                                finish()
                                return@launch
                            }
                            val fecha = selectedDate
                            val monedaOrigen = account.currency
                            val monedaDestino = cuentaDestino.currency
                            val montoDestino = if (monedaOrigen == monedaDestino) {
                                montoFinal
                            } else {
                                withContext(Dispatchers.IO) {
                                    val rate = db.currencyRateDao().getSmartRate(monedaOrigen, monedaDestino) ?: 1.0
                                    montoFinal * rate
                                }
                            }
                            val transOrigen = Transaction(
                                userId = userId,
                                amount = -montoFinal,
                                description = descripcion,
                                type = "Transferencia",
                                category = "Transferencia",
                                accountId = accountId,
                                date = fecha
                            )
                            val transDestino = Transaction(
                                userId = userId,
                                amount = montoDestino,
                                description = descripcion,
                                type = "Transferencia",
                                category = "Transferencia",
                                accountId = cuentaDestino.id,
                                date = fecha
                            )
                            withContext(Dispatchers.IO) {
                                db.transactionDao().insert(transOrigen)
                                db.transactionDao().insert(transDestino)
                            }
                            cargarCuentaYTransacciones()
                            alertDialog.dismiss()
                        } catch (e: Exception) {
                            if (e.message?.contains("No hay usuario actual") == true) {
                                Toast.makeText(this@AccountDetailActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                                startActivity(Intent(this@AccountDetailActivity, LoginActivity::class.java))
                                finish()
                            } else {
                                Toast.makeText(this@AccountDetailActivity, "Error al transferir: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            alertDialog.show()
        }
    }

    private fun mostrarDialogoCambioMoneda() {
        val db = AppDatabase.getInstance(applicationContext)
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_account_currency, null)
        val spinnerNewCurrency = dialogView.findViewById<Spinner>(R.id.spinnerNewCurrency)
        val textViewRateInfo = dialogView.findViewById<TextView>(R.id.textViewRateInfo)
        val currencies = listOf("CUP", "USD", "EUR", "MLC")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, currencies)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerNewCurrency.adapter = adapter
        val currentCurrency = account.currency
        spinnerNewCurrency.setSelection(currencies.indexOf(currentCurrency))
        
        // Mostrar información de la tasa
        fun updateRateInfo() {
            val newCurrency = currencies[spinnerNewCurrency.selectedItemPosition]
            if (newCurrency == currentCurrency) {
                textViewRateInfo.text = "Sin cambio de moneda"
            } else {
                lifecycleScope.launch {
                    val rate = withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(currentCurrency, newCurrency) ?: 1.0
                    }
                    // Calcular el nuevo saldo
                    val income = withContext(Dispatchers.IO) { db.transactionDao().getTotalIncomeByAccount(account.id) } ?: 0.0
                    val expense = withContext(Dispatchers.IO) { db.transactionDao().getTotalExpenseByAccount(account.id) } ?: 0.0
                    val transferIn = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferInByAccount(account.id) } ?: 0.0
                    val transferOut = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferOutByAccount(account.id) } ?: 0.0
                    val realBalance = account.initialBalance + income + expense + transferIn + transferOut
                    val newBalance = realBalance * rate
                    textViewRateInfo.text = "Tasa: 1 $currentCurrency = $rate $newCurrency\nNuevo saldo: %.2f $newCurrency".format(newBalance)
                }
            }
        }
        
        spinnerNewCurrency.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                updateRateInfo()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        
        // Actualizar info inicial
        updateRateInfo()
        
        AlertDialog.Builder(this)
            .setTitle("Cambiar moneda de la cuenta")
            .setView(dialogView)
            .setPositiveButton("Convertir") { dialog, which ->
                val newCurrency = currencies[spinnerNewCurrency.selectedItemPosition]
                if (newCurrency == currentCurrency) {
                    Toast.makeText(this, "La moneda ya es $newCurrency", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val rate = withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(currentCurrency, newCurrency) ?: 1.0
                    }
                    // Calcular saldo real actual
                    val income = withContext(Dispatchers.IO) { db.transactionDao().getTotalIncomeByAccount(account.id) } ?: 0.0
                    val expense = withContext(Dispatchers.IO) { db.transactionDao().getTotalExpenseByAccount(account.id) } ?: 0.0
                    val transferIn = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferInByAccount(account.id) } ?: 0.0
                    val transferOut = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferOutByAccount(account.id) } ?: 0.0
                    val realBalance = account.initialBalance + income + expense + transferIn + transferOut
                    val newInitialBalance = realBalance * rate
                    
                    // ✅ IMPORTANTE: AL CONVERTIR A CUP, exchangeRate = 1.0 (porque CUP es la moneda base)
                    // AL CONVERTIR A USD, exchangeRate = tasa de CUP a USD
                    val newExchangeRate = if (newCurrency == "CUP") {
                        1.0
                    } else if (newCurrency == "MLC") {
                        1.0 // MLC es equivalente a CUP
                    } else {
                        // Tasa de CUP a la nueva moneda (1 CUP = X nueva moneda)
                        withContext(Dispatchers.IO) {
                            db.currencyRateDao().getSmartRate("CUP", newCurrency) ?: 1.0
                        }
                    }
                    
                    val updatedAccount = account.copy(
                        currency = newCurrency,
                        initialBalance = newInitialBalance,
                        exchangeRate = newExchangeRate
                    )
                    withContext(Dispatchers.IO) { db.accountDao().update(updatedAccount) }
                    cargarCuentaYTransacciones()
                    Toast.makeText(this@AccountDetailActivity, 
                        "Moneda cambiada a $newCurrency\nNuevo saldo: %.2f $newCurrency".format(newInitialBalance), 
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarTransaccion(transaction: Transaction) {
        AlertDialog.Builder(this)
            .setTitle("Confirmar eliminación")
            .setMessage("¿Seguro que deseas eliminar esta transacción? Esta acción no se puede deshacer.")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) {
                        if (transaction.type == "Transferencia") {
                            db.transactionDao().deleteTransferPair(transaction.date, transaction.description, transaction.category)
                        } else {
                            db.transactionDao().deleteById(transaction.id)
                        }
                    }
                    cargarCuentaYTransacciones()
                    // Notificar a la pantalla principal para que recargue balances si es necesario
                    try {
                        val main = cu.rge.cartera.MainActivity::class.java
                        val intent = Intent(this@AccountDetailActivity, main)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
} 