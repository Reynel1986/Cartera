package cu.rge.cartera

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.google.android.material.navigation.NavigationView
import cu.rge.cartera.data.DataManager
import cu.rge.cartera.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import cu.rge.cartera.navigation.NavigationManager
import cu.rge.cartera.navigation.DataUpdateObserver
import cu.rge.cartera.navigation.DataUpdateListener
import cu.rge.cartera.navigation.DataUpdateType
import cu.rge.cartera.navigation.NavigationDestination
import cu.rge.cartera.navigation.BalanceCalculator
import android.app.DatePickerDialog
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.app.AlertDialog
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Category
import cu.rge.cartera.data.model.Transaction
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cu.rge.cartera.data.model.Account
import android.widget.TextView
import android.widget.LinearLayout
import cu.rge.cartera.data.model.AccountWithBalance
import kotlinx.coroutines.Job
import android.view.LayoutInflater
import android.view.ViewGroup
import cu.rge.cartera.data.model.PersonaRelacionada
import cu.rge.cartera.data.model.AbonoPersona
import java.text.SimpleDateFormat
import java.text.DecimalFormat
import java.util.Locale
import android.widget.ImageButton
import cu.rge.cartera.data.model.Producto
import cu.rge.cartera.data.model.VentaProducto
import cu.rge.cartera.data.model.CorteProducto

class MainActivity : AppCompatActivity(), DataUpdateListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dataManager: DataManager
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var navigationManager: NavigationManager
    private lateinit var dataUpdateObserver: DataUpdateObserver
    private lateinit var balanceCalculator: BalanceCalculator
    private lateinit var accountsRecyclerView: RecyclerView
    private lateinit var accountsAdapter: AccountSummaryAdapter
    private val accountsList = mutableListOf<Account>()
    private val accountsWithBalance = mutableListOf<AccountWithBalance>()
    private var loadBalanceJob: Job? = null
    private val loadBalanceMutex = Mutex()
    private lateinit var deudoresRecyclerView: RecyclerView
    private lateinit var acreedoresRecyclerView: RecyclerView
    private lateinit var deudoresAdapter: PersonasResumenAdapter
    private lateinit var acreedoresAdapter: PersonasResumenAdapter
    private val deudoresList = mutableListOf<PersonaRelacionada>()
    private val acreedoresList = mutableListOf<PersonaRelacionada>()
    private val loadPersonasMutex = Mutex()
    private lateinit var productosRecyclerView: RecyclerView
    private lateinit var productosAdapter: ProductosResumenAdapter
    private val productosList = mutableListOf<Producto>()
    private var uiInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        dataManager = DataManager.getInstance(applicationContext)
        navigationManager = NavigationManager.getInstance()
        dataUpdateObserver = DataUpdateObserver.getInstance()
        balanceCalculator = BalanceCalculator.getInstance()

        setContentView(binding.root)
        inicializarUI()

        // Corregir cuentas con exchangeRate incorrecto (una sola vez)
        lifecycleScope.launch {
            corregirCuentasConExchangeRateIncorrecto()
        }

        // Verificar usuario autenticado al iniciar
        lifecycleScope.launch {
            val isLogged = withContext(Dispatchers.IO) {
                try {
                    dataManager.getCurrentUser()
                    true
                } catch (e: Exception) {
                    false
                }
            }
            if (!isLogged) {
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
                return@launch
            }
            loadBalance()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Registrar observador para recibir notificaciones de cambios
        dataUpdateObserver.addObserver(this)
        // Asegurar que la UI esté inicializada
        if (!uiInitialized) {
            inicializarUI()
        }
        // Recargar datos al volver a la actividad
        lifecycleScope.launch { loadBalance() }
    }
    
    override fun onPause() {
        super.onPause()
        // Desregistrar observador para evitar memory leaks
        dataUpdateObserver.removeObserver(this)
    }
    
    override fun onDataChanged(updateType: DataUpdateType) {
        // Actualizar la UI en el hilo principal
        runOnUiThread {
            when (updateType) {
                DataUpdateType.ACCOUNTS, DataUpdateType.TRANSACTIONS, DataUpdateType.GENERAL -> {
                    lifecycleScope.launch { loadBalance() }
                }
                DataUpdateType.PERSONAS -> {
                    lifecycleScope.launch { loadPersonas() }
                }
                DataUpdateType.PRODUCTOS -> {
                    loadProductos()
                }
            }
        }
    }

    private fun inicializarUI() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.navigation_view)

        setupToolbar()
        setupButtons()
        updateMenuHeader()

        val menuButton: Button = findViewById(R.id.menuButton)
        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        navigationView.setNavigationItemSelectedListener { item ->
            val destination = when (item.itemId) {
                R.id.nav_profile -> NavigationDestination.PROFILE
                R.id.nav_settings -> NavigationDestination.SETTINGS
                R.id.nav_transacciones -> NavigationDestination.TRANSACTIONS
                R.id.nav_informes -> NavigationDestination.REPORTS
                R.id.nav_categorias -> NavigationDestination.CATEGORIES
                R.id.nav_accounts -> NavigationDestination.ACCOUNTS
                R.id.nav_deudores -> NavigationDestination.DEUDORES
                R.id.nav_acreedores -> NavigationDestination.ACREEDORES
                R.id.nav_productos -> NavigationDestination.PRODUCTOS
                R.id.nav_tarifa_pago -> NavigationDestination.TARIFA_PAGO
                else -> null
            }

            destination?.let {
                navigationManager.navigateTo(this, it)
                drawerLayout.closeDrawer(GravityCompat.START)
                true
            } ?: false
        }

        // Configurar RecyclerView de cuentas
        accountsRecyclerView = findViewById(R.id.accountsRecyclerView)
        val accountsLayoutManager = LinearLayoutManager(this)
        accountsLayoutManager.isAutoMeasureEnabled = true
        accountsRecyclerView.layoutManager = accountsLayoutManager
        accountsAdapter = AccountSummaryAdapter(accountsWithBalance)
        accountsRecyclerView.adapter = accountsAdapter

        deudoresRecyclerView = findViewById(R.id.deudoresRecyclerView)
        acreedoresRecyclerView = findViewById(R.id.acreedoresRecyclerView)
        deudoresAdapter = PersonasResumenAdapter(deudoresList)
        acreedoresAdapter = PersonasResumenAdapter(acreedoresList)
        val deudoresLayoutManager = LinearLayoutManager(this)
        deudoresLayoutManager.isAutoMeasureEnabled = true
        deudoresRecyclerView.layoutManager = deudoresLayoutManager
        val acreedoresLayoutManager = LinearLayoutManager(this)
        acreedoresLayoutManager.isAutoMeasureEnabled = true
        acreedoresRecyclerView.layoutManager = acreedoresLayoutManager
        android.util.Log.d("MainActivity", "Adjuntando adapter de deudores en inicializarUI")
        deudoresRecyclerView.adapter = deudoresAdapter
        android.util.Log.d("MainActivity", "Adjuntando adapter de acreedores en inicializarUI")
        acreedoresRecyclerView.adapter = acreedoresAdapter
        android.util.Log.d("MainActivity", "Adapters adjuntados: deudores=${deudoresRecyclerView.adapter != null}, acreedores=${acreedoresRecyclerView.adapter != null}")

        deudoresAdapter.onItemClick = { persona -> mostrarDetallePersona(persona) }
        acreedoresAdapter.onItemClick = { persona -> mostrarDetallePersona(persona) }

        productosRecyclerView = findViewById(R.id.productosRecyclerView)
        val productosLayoutManager = LinearLayoutManager(this)
        productosLayoutManager.isAutoMeasureEnabled = true
        productosRecyclerView.layoutManager = productosLayoutManager
        productosAdapter = ProductosResumenAdapter(productosList)
        productosRecyclerView.adapter = productosAdapter
        // Agregar click listener para mostrar detalle del producto
        productosAdapter.onItemClick = { producto -> mostrarDetalleProducto(producto) }

        // Configurar RecyclerView de transacciones (vacío por ahora)
        val transactionsRecyclerView: RecyclerView = findViewById(R.id.transactionsRecyclerView)
        transactionsRecyclerView.layoutManager = LinearLayoutManager(this)
        transactionsRecyclerView.adapter = null // Se configurará cuando se implemente

        uiInitialized = true
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                logout()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private suspend fun loadBalance() {
        loadBalanceMutex.withLock {
            loadBalanceJob?.cancel()
            loadBalanceJob = lifecycleScope.launch {
                try {
                    android.util.Log.d("MainActivity", "Iniciando loadBalance()")
                    val db = cu.rge.cartera.data.AppDatabase.getInstance(applicationContext)
                    val defaultCurrency = "CUP"
                    
                    // Usar la calculadora de balance
                    val balanceResult = balanceCalculator.calculateTotalBalance(db, defaultCurrency)
                    
                    // Actualizar listas locales
                    accountsList.clear()
                    accountsList.addAll(balanceResult.accountsWithBalance.map { it.account })
                    accountsWithBalance.clear()
                    accountsWithBalance.addAll(balanceResult.accountsWithBalance)
                    
                    android.util.Log.d("MainActivity", "Balance calculado - Total: ${balanceResult.totalBalance}, Ingresos: ${balanceResult.totalIncome}, Gastos: ${balanceResult.totalExpense}")
                    
                    // Actualizar UI en el hilo principal
                    runOnUiThread {
                        accountsAdapter.notifyDataSetChanged()

                        binding.totalBalance.text = String.format("%.2f %s", balanceResult.totalBalance, defaultCurrency)
                        binding.totalIncome.text = String.format("Ingresos: %.2f %s", balanceResult.totalIncome, defaultCurrency)
                        binding.totalExpense.text = String.format("Gastos: %.2f %s", balanceResult.totalExpense, defaultCurrency)
                        findViewById<TextView>(R.id.textViewTotalCuentas).text = "Total: %.2f %s".format(balanceResult.totalCuentas, defaultCurrency)

                        // Mostrar mes actual
                        val calendar = java.util.Calendar.getInstance()
                        val meses = arrayOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
                        val mesNombre = meses[calendar.get(java.util.Calendar.MONTH)]
                        val año = calendar.get(java.util.Calendar.YEAR)
                        findViewById<TextView>(R.id.textViewMesActual).text = "$mesNombre $año"

                        // Actualizar también el menú lateral
                        updateMenuHeader()
                    }

                    // Cargar deudores y acreedores con lógica de conversión
                    loadPersonas()

                    // Productos
                    val productos = withContext(Dispatchers.IO) { db.productoDao().getAll() }
                    productosList.clear()
                    productosList.addAll(productos)

                    // Calcular ganancias por producto y total vendido por producto
                    val gananciasPorProducto = mutableMapOf<Long, Double>()
                    val totalVendidoPorProducto = mutableMapOf<Long, Double>()
                    for (producto in productos) {
                        // Obtener el último corte para calcular solo desde esa fecha
                        val ultimoCorte = withContext(Dispatchers.IO) {
                            db.corteProductoDao().getUltimoCorte(producto.id)
                        }
                        val fechaDesde = ultimoCorte?.fechaCorte ?: java.util.Date(0) // Si no hay corte, usar fecha 0 (todas las ventas)

                        val totalVentas = withContext(Dispatchers.IO) {
                            db.ventaProductoDao().getTotalVentasByProductoDesde(producto.id, fechaDesde) ?: 0.0
                        }
                        val totalCantidadVendida = withContext(Dispatchers.IO) {
                            db.ventaProductoDao().getTotalCantidadVendidaByProductoDesde(producto.id, fechaDesde) ?: 0.0
                        }
                        val costoVentas = totalCantidadVendida * producto.purchasePrice
                        val ganancia = totalVentas - costoVentas
                        gananciasPorProducto[producto.id] = ganancia
                        // Total vendido es el costo de lo vendido (sin ganancia)
                        totalVendidoPorProducto[producto.id] = costoVentas

                        android.util.Log.d("MainActivity", "Producto: ${producto.name}")
                        android.util.Log.d("MainActivity", "  Stock actual: ${producto.quantity}")
                        android.util.Log.d("MainActivity", "  Precio compra: ${producto.purchasePrice}")
                        android.util.Log.d("MainActivity", "  Último corte: ${ultimoCorte?.fechaCorte}")
                        android.util.Log.d("MainActivity", "  Total ventas (desde corte): $totalVentas")
                        android.util.Log.d("MainActivity", "  Cantidad vendida (desde corte): $totalCantidadVendida")
                        android.util.Log.d("MainActivity", "  Costo ventas: $costoVentas")
                        android.util.Log.d("MainActivity", "  Ganancia: $ganancia")
                    }

                    // Actualizar adapter con las ganancias calculadas
                    productosAdapter = ProductosResumenAdapter(productosList, gananciasPorProducto, totalVendidoPorProducto)
                    productosRecyclerView.adapter = productosAdapter
                    productosAdapter.onItemClick = { producto -> mostrarDetalleProducto(producto) }

                    var totalInversion = 0.0
                    var totalGanancia = 0.0
                    var totalVendido = 0.0
                    for (p in productosList) {
                        val rate = if (p.currency == defaultCurrency) {
                            1.0
                        } else {
                            withContext(Dispatchers.IO) {
                                db.currencyRateDao().getSmartRate(p.currency, defaultCurrency) ?: 1.0
                            }
                        }
                        // Inversión: cantidad * precio de compra
                        totalInversion += p.quantity * p.purchasePrice * rate
                        // Ganancia: sumar la ganancia calculada por producto
                        totalGanancia += gananciasPorProducto[p.id] ?: 0.0
                        // Total vendido: sumar el total de ventas por producto
                        totalVendido += totalVendidoPorProducto[p.id] ?: 0.0
                    }
                    findViewById<TextView>(R.id.textViewTotalProductos).text = "Total Inv: %.2f %s".format(totalInversion, defaultCurrency)
                    findViewById<TextView>(R.id.textViewGananciaProductos).text = "Total Gan: %.2f %s".format(totalGanancia, defaultCurrency)
                    findViewById<TextView>(R.id.textViewTotalVendido).text = "Total Vendido: %.2f %s".format(totalVendido, defaultCurrency)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    // No mostrar error cuando se cancela el job (normal cuando hay llamadas rápidas)
                    android.util.Log.d("MainActivity", "loadBalance cancelado (normal)")
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error al cargar el balance", e)
                    Toast.makeText(this@MainActivity, "Error al cargar el balance: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupButtons() {
        binding.incomeButton.setOnClickListener {
            lifecycleScope.launch {
                val isLogged = withContext(Dispatchers.IO) {
                    try {
                        dataManager.getCurrentUser()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!isLogged) {
                    Toast.makeText(this@MainActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                if (accountsList.isNotEmpty()) {
                    showAddTransactionDialog(accountsList[0], "Ingreso")
                } else {
                    Toast.makeText(this@MainActivity, "Primero debes crear una cuenta bancaria", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.expenseButton.setOnClickListener {
            lifecycleScope.launch {
                val isLogged = withContext(Dispatchers.IO) {
                    try {
                        dataManager.getCurrentUser()
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                if (!isLogged) {
                    Toast.makeText(this@MainActivity, "Debes iniciar sesión para operar", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                if (accountsList.isNotEmpty()) {
                    showAddTransactionDialog(accountsList[0], "Gasto")
                } else {
                    Toast.makeText(this@MainActivity, "Primero debes crear una cuenta bancaria", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.transferButton.setOnClickListener {
            if (accountsList.size < 2) {
                Toast.makeText(this, "Debes tener al menos dos cuentas para transferir", Toast.LENGTH_SHORT).show()
            } else {
                showTransferDialog()
            }
        }
    }

    private fun showAddTransactionDialog(account: Account, type: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_transaction, null)
        val editTextAmount = dialogView.findViewById<EditText>(R.id.editTextAmount)
        val editTextDescription = dialogView.findViewById<EditText>(R.id.editTextDescription)
        val spinnerCategory = dialogView.findViewById<Spinner>(R.id.spinnerCategory)
        val buttonAdd = dialogView.findViewById<Button>(R.id.buttonAddTransaction)
        val spinnerAccount = dialogView.findViewById<Spinner>(R.id.spinnerAccount)
        val spinnerTarifa = dialogView.findViewById<Spinner>(R.id.spinnerTarifaPago)
        val textViewMontoFinal = dialogView.findViewById<TextView>(R.id.textViewMontoFinal)
        val editTextDate = dialogView.findViewById<EditText>(R.id.editTextDate)
        val buttonDatePicker = dialogView.findViewById<Button>(R.id.buttonDatePicker)
        val buttonCalculator = dialogView.findViewById<Button>(R.id.buttonCalculator)
        
        // Variable para almacenar la fecha seleccionada
        var selectedDate = Date()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val decimalFormat = DecimalFormat("#.##")
        editTextDate.setText(dateFormat.format(selectedDate))

        // Poblar spinners
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val accounts = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val categories = withContext(Dispatchers.IO) { db.categoryDao().getAll() }
            val tarifas = withContext(Dispatchers.IO) { db.tarifaPagoDao().getAll() }

            val adapterAccount = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, accounts.map { it.name })
            adapterAccount.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerAccount.adapter = adapterAccount

            val adapterCategory = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, categories.map { it.name })
            adapterCategory.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = adapterCategory

            // Asegurar que la opción por defecto sea 'Sin tarifa (0%)'
            val tarifaDefault = cu.rge.cartera.data.model.TarifaPago(nombre = "Sin tarifa", porcentaje = 0.0, descripcion = "")
            val listaTarifas = listOf(tarifaDefault) + tarifas.filter { it.porcentaje > 0.0 }
            val nombresTarifas = listaTarifas.map { "${it.nombre} (${it.porcentaje}%)" }
            val adapterTarifa = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, nombresTarifas)
            adapterTarifa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerTarifa.adapter = adapterTarifa
            spinnerTarifa.setSelection(0) // Por defecto 'Sin tarifa (0%)'

            // Seleccionar cuenta
            val accountIndex = accounts.indexOfFirst { it.id == account.id }
            if (accountIndex >= 0) {
                spinnerAccount.setSelection(accountIndex)
            }

            // Actualizar monto final en tiempo real
            fun actualizarMontoFinal() {
                val monto = editTextAmount.text.toString().toDoubleOrNull() ?: 0.0
                val posTarifa = spinnerTarifa.selectedItemPosition
                val porcentaje = if (posTarifa > 0 && posTarifa in listaTarifas.indices) listaTarifas[posTarifa].porcentaje else 0.0
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
                val calculator = CalculatorDialog(this@MainActivity)
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
                    this@MainActivity,
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

            val dialog = AlertDialog.Builder(this@MainActivity)
                .setTitle("Nuevo $type")
                .setView(dialogView)
                .create()

            buttonAdd.setOnClickListener {
                val amountStr = editTextAmount.text.toString()
                val description = editTextDescription.text.toString()
                val categoryName = spinnerCategory.selectedItem?.toString() ?: ""
                val amount = amountStr.toDoubleOrNull()
                val selectedAccountName = spinnerAccount.selectedItem?.toString() ?: ""
                val posTarifa = spinnerTarifa.selectedItemPosition
                val porcentaje = if (posTarifa > 0 && posTarifa in listaTarifas.indices) listaTarifas[posTarifa].porcentaje else 0.0
                val rebaja = (amount ?: 0.0) * porcentaje / 100.0
                val montoFinal = (amount ?: 0.0) - rebaja

                if (amount == null || amount <= 0.0) {
                    Toast.makeText(this@MainActivity, "Monto inválido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (categoryName.isBlank()) {
                    Toast.makeText(this@MainActivity, "Selecciona una categoría", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val accounts = withContext(Dispatchers.IO) {
                        db.accountDao().getAll()
                    }
                    val accountSelected = accounts.find { it.name == selectedAccountName }
                    val categories = withContext(Dispatchers.IO) {
                        db.categoryDao().getAll()
                    }
                    val category = categories.find { it.name == categoryName }
                    val limit = category?.monthlyLimit

                    if (limit != null && limit > 0) {
                        val now = Calendar.getInstance()
                        now.set(Calendar.DAY_OF_MONTH, 1)
                        val startDate = now.time
                        now.add(Calendar.MONTH, 1)
                        now.set(Calendar.DAY_OF_MONTH, 1)
                        val endDate = Date(now.timeInMillis - 1)
                        val userId = withContext(Dispatchers.IO) {
                            dataManager.getCurrentUser().email
                        }
                        val transactions = withContext(Dispatchers.IO) {
                            db.transactionDao().getTransactionsByUser(userId)
                                .filter { it.category == categoryName && it.date >= startDate && it.date <= endDate && it.accountId == accountSelected?.id }
                        }
                        val totalMes = transactions.sumOf { it.amount }
                        val nuevoTotal = totalMes + montoFinal

                        if (nuevoTotal > limit) {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Límite mensual superado")
                                .setMessage("Vas a superar el límite establecido para esta categoría. ¿Deseas continuar?")
                                .setPositiveButton("Continuar") { _, _ ->
                                    lifecycleScope.launch {
                                        guardarTransaccion(userId, if (type == "Gasto") -kotlin.math.abs(montoFinal) else montoFinal, description, type, categoryName, accountSelected?.id, selectedDate)
                                        dialog.dismiss()
                                    }
                                }
                                .setNegativeButton("Cancelar", null)
                                .show()
                            return@launch
                        }
                    }
                    val userId = withContext(Dispatchers.IO) { dataManager.getCurrentUser().email }
                    guardarTransaccion(userId, if (type == "Gasto") -kotlin.math.abs(montoFinal) else montoFinal, description, type, categoryName, accountSelected?.id, selectedDate)
                    dialog.dismiss()
                }
            }
            dialog.show()
        }
    }

    private suspend fun guardarTransaccion(userId: String, amount: Double, description: String, type: String, category: String, accountId: Long?, date: Date = Date()) {
        val db = AppDatabase.getInstance(applicationContext)
        val transaction = cu.rge.cartera.data.model.Transaction(
            userId = userId,
            amount = amount,
            description = description,
            type = type,
            category = category,
            accountId = accountId,
            date = date
        )
                        withContext(Dispatchers.IO) {
                            db.transactionDao().insert(transaction)
                        }
        android.util.Log.d("MainActivity", "Transacción guardada: $type - $amount - $description")
        Toast.makeText(this@MainActivity, "Transacción guardada", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch { loadBalance() }
        // Notificar cambios en transacciones para que otras pantallas se actualicen
        dataUpdateObserver.notifyTransactionsChanged()
    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                dataManager.logout()
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error al cerrar sesión", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun showTransferDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_transfer, null)
        val spinnerOrigen = Spinner(this)
        val spinnerDestino = dialogView.findViewById<Spinner>(R.id.spinnerDestino)
        val editTextMonto = dialogView.findViewById<EditText>(R.id.editTextMonto)
        val editTextDescripcion = dialogView.findViewById<EditText>(R.id.editTextDescripcion)
        val textViewConversion = TextView(this)
        textViewConversion.setPadding(0, 16, 0, 0)
        // Insertar spinnerOrigen y textViewConversion en el layout
        (dialogView as? LinearLayout)?.addView(spinnerOrigen, 0)
        (dialogView as? LinearLayout)?.addView(textViewConversion)

        // Poblar ambos spinners
        val accountNames = accountsList.map { it.name }
        val adapterOrigen = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
        adapterOrigen.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerOrigen.adapter = adapterOrigen
        val adapterDestino = ArrayAdapter(this, android.R.layout.simple_spinner_item, accountNames)
        adapterDestino.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerDestino.adapter = adapterDestino
        spinnerDestino.setSelection(1.coerceAtMost(accountNames.size - 1))

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Transferir entre cuentas")
            .setView(dialogView)
            .setPositiveButton("Transferir", null)
            .setNegativeButton("Cancelar", null)
            .create()
        alertDialog.setOnShowListener {
            fun actualizarConversion() {
                val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                val posOrigen = spinnerOrigen.selectedItemPosition
                val posDestino = spinnerDestino.selectedItemPosition
                if (posOrigen == -1 || posDestino == -1 || posOrigen == posDestino || monto <= 0.0) {
                    textViewConversion.text = ""
                    return
                }
                val accountFrom = accountsList[posOrigen]
                val accountTo = accountsList[posDestino]
                val monedaOrigen = accountFrom.currency
                val monedaDestino = accountTo.currency
                if (monedaOrigen == monedaDestino) {
                    textViewConversion.text = "Sin conversión de moneda"
                } else {
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(applicationContext)
                        val rate = withContext(Dispatchers.IO) {
                            db.currencyRateDao().getSmartRate(monedaOrigen, monedaDestino) ?: 1.0
                        }
                        val montoDestino = monto * rate
                        textViewConversion.text = "Tasa: 1 $monedaOrigen = $rate $monedaDestino\nRecibirá: %.2f $monedaDestino".format(montoDestino)
                    }
                }
            }
            spinnerOrigen.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) { actualizarConversion() }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
            spinnerDestino.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) { actualizarConversion() }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
            }
            editTextMonto.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { actualizarConversion() }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                val descripcion = editTextDescripcion.text.toString()
                val posOrigen = spinnerOrigen.selectedItemPosition
                val posDestino = spinnerDestino.selectedItemPosition
                if (posOrigen == -1 || posDestino == -1 || posOrigen == posDestino) {
                    Toast.makeText(this, "Selecciona cuentas válidas", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (monto <= 0.0) {
                    Toast.makeText(this, "Monto inválido", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val accountFrom = accountsList[posOrigen]
                val accountTo = accountsList[posDestino]
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val now = Date()
                    try {
                        val userId = withContext(Dispatchers.IO) {
                            dataManager.getCurrentUser().email
                        }
                        val monedaOrigen = accountFrom.currency
                        val monedaDestino = accountTo.currency
                        val montoDestino = if (monedaOrigen == monedaDestino) {
                            monto
                        } else {
                            withContext(Dispatchers.IO) {
                                val rate = db.currencyRateDao().getSmartRate(monedaOrigen, monedaDestino) ?: 1.0
                                monto * rate
                            }
                        }
                        val transFrom = cu.rge.cartera.data.model.Transaction(
                            userId = userId,
                            amount = -monto,
                            description = "Transferencia a ${accountTo.name}. $descripcion",
                            type = "Transferencia",
                            category = "Transferencia",
                            accountId = accountFrom.id,
                            date = now
                        )
                        val transTo = cu.rge.cartera.data.model.Transaction(
                            userId = userId,
                            amount = montoDestino,
                            description = "Transferencia desde ${accountFrom.name}. $descripcion",
                            type = "Transferencia",
                            category = "Transferencia",
                            accountId = accountTo.id,
                            date = now
                        )
                        withContext(Dispatchers.IO) {
                            db.transactionDao().insert(transFrom)
                            db.transactionDao().insert(transTo)
                        }
                        Toast.makeText(this@MainActivity, "Transferencia realizada", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss()
                        loadBalance()
                        // Notificar cambios en transacciones
                        dataUpdateObserver.notifyTransactionsChanged()
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Error al transferir", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        alertDialog.show()
    }

    private fun mostrarDetallePersona(persona: PersonaRelacionada) {
        Toast.makeText(this@MainActivity, "Abriendo detalle de persona", Toast.LENGTH_SHORT).show()
        val dialogView = layoutInflater.inflate(R.layout.dialog_detalle_persona, null)

        // Referencias UI
        val textViewNombre = dialogView.findViewById<TextView>(R.id.textViewNombrePersona)
        val textViewDescripcion = dialogView.findViewById<TextView>(R.id.textViewDescripcionPersona)
        val textViewMontoOriginal = dialogView.findViewById<TextView>(R.id.textViewMontoOriginal)
        val textViewMontoPendiente = dialogView.findViewById<TextView>(R.id.textViewMontoPendiente)
        val textViewEstadoDeuda = dialogView.findViewById<TextView>(R.id.textViewEstadoDeuda)
        val recyclerViewAbonos = dialogView.findViewById<RecyclerView>(R.id.recyclerViewAbonos)
        val buttonExpandirAbono = dialogView.findViewById<Button>(R.id.buttonExpandirAbono)
        val layoutFormularioAbono = dialogView.findViewById<LinearLayout>(R.id.layoutFormularioAbono)
        val editTextMontoAbono = dialogView.findViewById<EditText>(R.id.editTextMontoAbono)
        val editTextNotaAbono = dialogView.findViewById<EditText>(R.id.editTextNotaAbono)
        val spinnerCuentaAbono = dialogView.findViewById<Spinner>(R.id.spinnerCuentaAbono)
        val buttonConfirmarAbono = dialogView.findViewById<Button>(R.id.buttonConfirmarAbono)
        val buttonLiquidarDeuda = dialogView.findViewById<Button>(R.id.buttonLiquidarDeuda)

        textViewNombre.text = persona.nombre
        textViewDescripcion.text = persona.descripcion ?: ""
        textViewMontoOriginal.text = "Monto original: %.2f %s".format(persona.monto, persona.moneda)

        // Adapter de abonos
        val abonosList = mutableListOf<AbonoPersona>()
        val abonosAdapter = AbonosAdapter(abonosList) { abono -> eliminarAbono(abono) }
        recyclerViewAbonos.layoutManager = LinearLayoutManager(this)
        recyclerViewAbonos.adapter = abonosAdapter

        fun actualizarPendienteYHistorial() {
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val abonos = withContext(Dispatchers.IO) {
                    db.abonoPersonaDao().getByPersona(persona.id)
                }
                abonosList.clear()
                abonosList.addAll(abonos)
                abonosAdapter.notifyDataSetChanged()
                var pendiente = persona.monto
                for (ab in abonos) {
                    val rate = if (ab.moneda == persona.moneda) {
                        1.0
                    } else {
                        withContext(Dispatchers.IO) {
                            db.currencyRateDao().getSmartRate(ab.moneda, persona.moneda) ?: 1.0
                        }
                    }
                    pendiente += ab.monto * rate
                }
                textViewMontoPendiente.text = "Monto pendiente: %.2f %s".format(pendiente, persona.moneda)
                textViewEstadoDeuda.text = if (pendiente <= 0.01) "Pagada" else ""
            }
        }
        actualizarPendienteYHistorial()

        // Lógica de mostrar/ocultar formulario
        buttonExpandirAbono.setOnClickListener {
            Toast.makeText(this@MainActivity, "Click en expansor de abono", Toast.LENGTH_SHORT).show()
            if (layoutFormularioAbono.visibility == View.GONE) {
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                    if (cuentas.isEmpty()) {
                        Toast.makeText(this@MainActivity, "No hay cuentas disponibles. Crea una cuenta primero.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val listaCuentas = listOf("Sin cuenta") + cuentas.map { "${it.name} (${it.currency})" }
                    val adapterCuentas = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listaCuentas)
                    adapterCuentas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCuentaAbono.adapter = adapterCuentas
                    layoutFormularioAbono.visibility = View.VISIBLE
                    buttonExpandirAbono.text = "Cancelar"
                }
            } else {
                layoutFormularioAbono.visibility = View.GONE
                buttonExpandirAbono.text = "Nuevo préstamo"
                editTextMontoAbono.text.clear()
                editTextNotaAbono.text.clear()
            }
        }

        buttonConfirmarAbono.setOnClickListener {
            Toast.makeText(this@MainActivity, "Click en Registrar abono", Toast.LENGTH_SHORT).show()
            val monto = editTextMontoAbono.text.toString().toDoubleOrNull() ?: 0.0
            val nota = editTextNotaAbono.text.toString().trim().ifEmpty { null }
            val posCuenta = spinnerCuentaAbono.selectedItemPosition
            if (monto <= 0.0) {
                Toast.makeText(this, "Monto inválido", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (posCuenta < 0) {
                Toast.makeText(this, "Selecciona una cuenta", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (posCuenta == 0) {
                // Solo registrar abono, sin transacción bancaria
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                    withContext(Dispatchers.IO) {
                        db.abonoPersonaDao().insert(
                            cu.rge.cartera.data.model.AbonoPersona(
                                personaId = persona.id,
                                monto = monto,
                                moneda = persona.moneda,
                                nota = nota,
                                transactionId = null
                            )
                        )
                    }
                    editTextMontoAbono.text.clear()
                    editTextNotaAbono.text.clear()
                    layoutFormularioAbono.visibility = View.GONE
                    buttonExpandirAbono.text = "Nuevo préstamo"
                    actualizarPendienteYHistorial()
                    Toast.makeText(this@MainActivity, "Abono registrado (sin cuenta)", Toast.LENGTH_SHORT).show()
                    loadBalance()
                }
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                val cuentaSeleccionada = cuentas[posCuenta - 1]
                val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                val descripcionTrans = if (persona.tipo == "DEUDOR") {
                    "Nuevo préstamo a ${persona.nombre}" + (if (!nota.isNullOrBlank()) ": $nota" else "")
                } else {
                    "Nuevo préstamo de ${persona.nombre}" + (if (!nota.isNullOrBlank()) ": $nota" else "")
                }
                val tipoTrans = if (persona.tipo == "DEUDOR") "Gasto" else "Ingreso"
                val montoTrans = if (persona.moneda == cuentaSeleccionada.currency) {
                    // Para nuevo préstamo, el monto siempre es positivo
                    // Se registrará como gasto para deudores e ingreso para acreedores
                    if (persona.tipo == "DEUDOR") -kotlin.math.abs(monto) else kotlin.math.abs(monto)
                } else {
                    val rate = withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(persona.moneda, cuentaSeleccionada.currency) ?: 1.0
                    }
                    val montoConvertido = monto * rate
                    if (persona.tipo == "DEUDOR") -kotlin.math.abs(montoConvertido) else kotlin.math.abs(montoConvertido)
                }
                val trans = cu.rge.cartera.data.model.Transaction(
                    userId = userId,
                    amount = montoTrans,
                    description = descripcionTrans,
                    type = tipoTrans,
                    category = "Abono ${persona.tipo}",
                    accountId = cuentaSeleccionada.id,
                    date = java.util.Date()
                )
                val transactionId = withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                withContext(Dispatchers.IO) {
                    db.abonoPersonaDao().insert(
                        cu.rge.cartera.data.model.AbonoPersona(
                            personaId = persona.id,
                            monto = monto,
                            moneda = persona.moneda,
                            nota = nota,
                            transactionId = transactionId
                        )
                    )
                }
                editTextMontoAbono.text.clear()
                editTextNotaAbono.text.clear()
                layoutFormularioAbono.visibility = View.GONE
                buttonExpandirAbono.text = "Nuevo préstamo"
                actualizarPendienteYHistorial()
                Toast.makeText(this@MainActivity, "Abono registrado y transacción creada", Toast.LENGTH_SHORT).show()
                loadBalance()
                // Notificar cambios en transacciones y personas
                dataUpdateObserver.notifyTransactionsChanged()
                dataUpdateObserver.notifyPersonasChanged()
            }
        }

        buttonLiquidarDeuda.setOnClickListener {
            val dialogView = layoutInflater.inflate(R.layout.dialog_liquidar_deuda, null)
            val spinnerCuenta = dialogView.findViewById<Spinner>(R.id.spinnerCuenta)
            val textViewSaldoCuenta = dialogView.findViewById<TextView>(R.id.textViewSaldoCuenta)
            val editTextMonto = dialogView.findViewById<EditText>(R.id.editTextMonto)
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                val listaCuentas = listOf("Sin cuenta") + cuentas.map { "${it.name} (${it.currency})" }
                val adapterCuentas = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, listaCuentas)
                adapterCuentas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCuenta.adapter = adapterCuentas
                spinnerCuenta.setSelection(0)
                spinnerCuenta.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: android.widget.AdapterView<*>, view: android.view.View?, position: Int, id: Long) {
                        if (position == 0) {
                            textViewSaldoCuenta.text = "Saldo disponible: N/A"
                        } else {
                            val cuenta = cuentas[position - 1]
                            lifecycleScope.launch {
                                val income = withContext(Dispatchers.IO) { db.transactionDao().getTotalIncomeByAccount(cuenta.id) } ?: 0.0
                                val expense = withContext(Dispatchers.IO) { db.transactionDao().getTotalExpenseByAccount(cuenta.id) } ?: 0.0
                                val transferIn = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferInByAccount(cuenta.id) } ?: 0.0
                                val transferOut = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferOutByAccount(cuenta.id) } ?: 0.0
                                val saldo = cuenta.initialBalance + income + expense + transferIn + transferOut
                                textViewSaldoCuenta.text = "Saldo disponible: %.2f %s".format(saldo, cuenta.currency)
                            }
                        }
                    }
                    override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
                }
            }
            val alertDialog = AlertDialog.Builder(this@MainActivity)
                .setTitle(if (persona.tipo == "DEUDOR") "Aumentar deuda" else "Liquidar acreencia")
                .setView(dialogView)
                .setPositiveButton("Liquidar", null)
                .setNegativeButton("Cancelar", null)
                .create()
            alertDialog.setOnShowListener {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val monto = editTextMonto.text.toString().toDoubleOrNull() ?: (textViewMontoPendiente.text.toString().substringAfter(": ").substringBefore(" ").toDoubleOrNull() ?: 0.0)
                    val posCuenta = spinnerCuenta.selectedItemPosition
                    val nota = "Liquidación total"
                    if (monto <= 0.0) {
                        Toast.makeText(this@MainActivity, "Monto inválido", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (posCuenta < 0) {
                        Toast.makeText(this@MainActivity, "Selecciona una cuenta", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (posCuenta == 0) {
                        // Solo registrar abono, sin transacción bancaria
                        lifecycleScope.launch {
                            val db = AppDatabase.getInstance(applicationContext)
                            val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                            withContext(Dispatchers.IO) {
                                db.abonoPersonaDao().insert(
                                    cu.rge.cartera.data.model.AbonoPersona(
                                        personaId = persona.id,
                                        // El monto debe ser negativo para reducir la deuda/acreedor
                            monto = if (persona.tipo == "DEUDOR") -kotlin.math.abs(monto) else -kotlin.math.abs(monto),
                                        moneda = persona.moneda,
                                        nota = nota,
                                        transactionId = null
                                    )
                                )
                            }
                            actualizarPendienteYHistorial()
                            // Notificar cambios para actualizar la UI
                            dataUpdateObserver.notifyPersonasChanged()
                            dataUpdateObserver.notifyDataChanged(DataUpdateType.TRANSACTIONS)
                            Toast.makeText(this@MainActivity, "Deuda liquidada (sin cuenta)", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        }
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(applicationContext)
                        val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                        val cuentaSeleccionada = cuentas[posCuenta - 1]
                        val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                        // Validar saldo suficiente solo para ACREEDOR (cuando tú pagas)
                        if (persona.tipo == "ACREEDOR") {
                            val income = withContext(Dispatchers.IO) { db.transactionDao().getTotalIncomeByAccount(cuentaSeleccionada.id) } ?: 0.0
                            val expense = withContext(Dispatchers.IO) { db.transactionDao().getTotalExpenseByAccount(cuentaSeleccionada.id) } ?: 0.0
                            val transferIn = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferInByAccount(cuentaSeleccionada.id) } ?: 0.0
                            val transferOut = withContext(Dispatchers.IO) { db.transactionDao().getTotalTransferOutByAccount(cuentaSeleccionada.id) } ?: 0.0
                            val saldoCuenta = cuentaSeleccionada.initialBalance + income + expense + transferIn + transferOut
                            val montoEnMonedaCuenta = if (persona.moneda == cuentaSeleccionada.currency) {
                                monto
                            } else {
                                val rate = withContext(Dispatchers.IO) {
                                    db.currencyRateDao().getSmartRate(persona.moneda, cuentaSeleccionada.currency) ?: 1.0
                                }
                                monto * rate
                            }
                            if (saldoCuenta < montoEnMonedaCuenta) {
                                Toast.makeText(this@MainActivity, "Saldo insuficiente en la cuenta seleccionada", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                        val descripcionTrans = if (persona.tipo == "DEUDOR") {
                            "Pago recibido de ${persona.nombre} (liquidación de deuda)"
                        } else {
                            "Pago realizado a ${persona.nombre} (liquidación de acreencia)"
                        }
                        val tipoTrans = if (persona.tipo == "DEUDOR") "Ingreso" else "Gasto"
                        val montoTrans = if (persona.moneda == cuentaSeleccionada.currency) {
                            // Para liquidación, el monto es positivo para ingresos (cuando un deudor paga)
                            // y negativo para gastos (cuando pagas a un acreedor)
                            if (persona.tipo == "DEUDOR") kotlin.math.abs(monto) else -kotlin.math.abs(monto)
                        } else {
                            val rate = withContext(Dispatchers.IO) {
                                db.currencyRateDao().getSmartRate(persona.moneda, cuentaSeleccionada.currency) ?: 1.0
                            }
                            val montoConvertido = monto * rate
                            if (persona.tipo == "DEUDOR") kotlin.math.abs(montoConvertido) else -kotlin.math.abs(montoConvertido)
                        }
                        val trans = cu.rge.cartera.data.model.Transaction(
                            userId = userId,
                            amount = montoTrans,
                            description = descripcionTrans,
                            type = tipoTrans,
                            category = "Abono ${persona.tipo}",
                            accountId = cuentaSeleccionada.id,
                            date = java.util.Date()
                        )
                        val transactionId = withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                        
                        // Calcular el saldo actual: monto inicial + abonos
                        val abonos = withContext(Dispatchers.IO) {
                            db.abonoPersonaDao().getByPersona(persona.id)
                        }
                        var saldoActual = persona.monto
                        for (ab in abonos) {
                            val rate = if (ab.moneda == persona.moneda) {
                                1.0
                            } else {
                                withContext(Dispatchers.IO) {
                                    db.currencyRateDao().getSmartRate(ab.moneda, persona.moneda) ?: 1.0
                                }
                            }
                            saldoActual += ab.monto * rate
                        }
                        
                        // Para deudor: el pago reduce la deuda (saldo negativo), para acreedor: el pago reduce la acreencia (saldo positivo)
                        val montoAbono = if (persona.tipo == "DEUDOR") -kotlin.math.abs(monto) else -kotlin.math.abs(monto)
                        val nuevoSaldo = saldoActual + montoAbono

                        // Verificar si hay cambio de deudor a acreedor o viceversa
                        // Para deudor: saldo negativo = pagó más de lo debido -> convertir a acreedor
                        // Para acreedor: saldo negativo = pagó más de lo debido -> convertir a deudor
                        if (nuevoSaldo < 0) {
                            
                            // Registrar el abono primero
                            val abonoId = withContext(Dispatchers.IO) {
                                db.abonoPersonaDao().insert(
                                    cu.rge.cartera.data.model.AbonoPersona(
                                        personaId = persona.id,
                                        monto = montoAbono,
                                        moneda = persona.moneda,
                                        nota = "$nota (Cambio de tipo)",
                                        transactionId = transactionId
                                    )
                                )
                            }
                            
                            // Cambiar el tipo de persona directamente en el mismo registro
                            val nuevoTipo = if (persona.tipo == "DEUDOR") "ACREEDOR" else "DEUDOR"
                            
                            // Actualizar el tipo de la persona con el nuevo monto (valor absoluto del saldo)
                            val nuevoMonto = kotlin.math.abs(nuevoSaldo)
                            withContext(Dispatchers.IO) {
                                db.personaRelacionadaDao().update(
                                    persona.copy(
                                        tipo = nuevoTipo,
                                        monto = nuevoMonto,
                                        fecha = Date()
                                    )
                                )
                            }
                            
                            // Mostrar mensaje informativo
                            val mensajeCambio = if (nuevoTipo == "ACREEDOR") {
                                "${persona.nombre} ahora es un ACREEDOR con un saldo de ${String.format("%.2f", nuevoMonto)} ${persona.moneda}"
                            } else {
                                "${persona.nombre} ahora es un DEUDOR con un saldo de ${String.format("%.2f", nuevoMonto)} ${persona.moneda}"
                            }
                            
                            actualizarPendienteYHistorial()
                            
                            // Mostrar mensaje con el cambio de estado
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Cambio de estado")
                                .setMessage(mensajeCambio)
                                .setPositiveButton("Aceptar") { dialog, _ ->
                                    dialog.dismiss()
                                    alertDialog.dismiss()
                                }
                                .show()
                        } else {
                            // Registrar el abono normal sin cambio de estado
                            val abonoId = withContext(Dispatchers.IO) {
                                db.abonoPersonaDao().insert(
                                    cu.rge.cartera.data.model.AbonoPersona(
                                        personaId = persona.id,
                                        monto = montoAbono,
                                        moneda = persona.moneda,
                                        nota = nota,
                                        transactionId = transactionId
                                    )
                                )
                            }
                            
                            actualizarPendienteYHistorial()
                            Toast.makeText(this@MainActivity, "Pago registrado correctamente", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                        }
                        // Notificar cambios en transacciones y personas
                        dataUpdateObserver.notifyTransactionsChanged()
                        dataUpdateObserver.notifyPersonasChanged()
                    }
                }
            }
            alertDialog.show()
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Detalle de ${persona.nombre}")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()
        alertDialog.show()
    }

    private fun eliminarAbono(abono: AbonoPersona) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            withContext(Dispatchers.IO) {
                abono.transactionId?.let { db.transactionDao().deleteById(it) }
                db.abonoPersonaDao().delete(abono)
            }
            Toast.makeText(this@MainActivity, "Abono y transacción eliminados", Toast.LENGTH_SHORT).show()
            loadBalance()
        }
    }

    private fun eliminarTransaccionGlobal(transaction: Transaction) {
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
                    loadBalance()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDetalleProducto(producto: Producto) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detalle_producto, null)
        val textViewNombre = dialogView.findViewById<TextView>(R.id.textViewNombreProducto)
        val textViewStock = dialogView.findViewById<TextView>(R.id.textViewStockProducto)
        val textViewPrecioCompra = dialogView.findViewById<TextView>(R.id.textViewPrecioCompraProducto)
        val textViewPrecioVenta = dialogView.findViewById<TextView>(R.id.textViewPrecioVentaProducto)
        val textViewCantidadTotalComprada = dialogView.findViewById<TextView>(R.id.textViewCantidadTotalComprada)
        val textViewInversionTotal = dialogView.findViewById<TextView>(R.id.textViewInversionTotal)
        val textViewInversionExistente = dialogView.findViewById<TextView>(R.id.textViewInversionExistente)
        val textViewGananciaPotencial = dialogView.findViewById<TextView>(R.id.textViewGananciaPotencial)
        val textViewTotalVendido = dialogView.findViewById<TextView>(R.id.textViewTotalVendido)
        val recyclerViewVentas = dialogView.findViewById<RecyclerView>(R.id.recyclerViewVentas)
        val editTextCantidadVenta = dialogView.findViewById<EditText>(R.id.editTextCantidadVenta)
        val editTextPrecioVenta = dialogView.findViewById<EditText>(R.id.editTextPrecioVenta)
        val buttonCalculatorVenta = dialogView.findViewById<Button>(R.id.buttonCalculatorVenta)
        val editTextFechaVenta = dialogView.findViewById<EditText>(R.id.editTextFechaVenta)
        val buttonDatePickerVenta = dialogView.findViewById<Button>(R.id.buttonDatePickerVenta)
        val editTextNotaVenta = dialogView.findViewById<EditText>(R.id.editTextNotaVenta)
        val buttonRegistrarVenta = dialogView.findViewById<Button>(R.id.buttonRegistrarVenta)
        val buttonIncrementarStock = dialogView.findViewById<Button>(R.id.buttonIncrementarStock)
        val buttonEliminarProducto = dialogView.findViewById<Button>(R.id.buttonEliminarProducto)
        val buttonReiniciarContadores = dialogView.findViewById<Button>(R.id.buttonReiniciarContadores)
        val buttonVerCortes = dialogView.findViewById<Button>(R.id.buttonVerCortes)

        val decimalFormat = DecimalFormat("#.##")
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

        // Variable para almacenar la fecha seleccionada
        var selectedDate = java.util.Date()
        editTextFechaVenta.setText(dateFormat.format(selectedDate))

        textViewNombre.text = producto.name
        textViewStock.text = "Stock: %.2f %s".format(producto.quantity, producto.unit)
        textViewPrecioCompra.text = "Precio de compra: %.2f %s".format(producto.purchasePrice, producto.currency)
        textViewPrecioVenta.text = "Precio de venta: %.2f %s".format(producto.sellingPrice, producto.currency)

        // Variable mutable para mantener el producto actualizado
        var productoActual = producto

        fun actualizarStockEnDialogo() {
            textViewStock.text = "Stock: %.2f %s".format(productoActual.quantity, productoActual.unit)
        }

        val ventasList = mutableListOf<VentaProducto>()
        lateinit var ventasAdapter: VentasAdapter
        
        ventasAdapter = VentasAdapter(ventasList) { venta -> eliminarVenta(venta, productoActual) { productoActualizado ->
            productoActual = productoActualizado
            actualizarStockEnDialogo()
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val ventas = withContext(Dispatchers.IO) { db.ventaProductoDao().getByProducto(productoActual.id) }
                ventasList.clear()
                ventasList.addAll(ventas)
                ventasAdapter.notifyDataSetChanged()

                // Calcular inversión total (cantidad total comprada × precio compra actual)
                val inversionTotal = productoActual.cantidadTotalComprada * productoActual.purchasePrice
                // Calcular inversión existente (stock actual × precio de compra)
                val inversionExistente = productoActual.quantity * productoActual.purchasePrice
                // Calcular ganancia potencial (stock actual × (precio venta - precio compra))
                val gananciaPotencial = productoActual.quantity * (productoActual.sellingPrice - productoActual.purchasePrice)
                // Calcular total vendido (costo de lo vendido, sin ganancia)
                val totalCantidadVendida = withContext(Dispatchers.IO) {
                    db.ventaProductoDao().getTotalCantidadVendidaByProducto(productoActual.id) ?: 0.0
                }
                val totalVendido = totalCantidadVendida * productoActual.purchasePrice
                
                textViewCantidadTotalComprada.text = "Cantidad total comprada: %.2f %s".format(productoActual.cantidadTotalComprada, productoActual.unit)
                textViewInversionTotal.text = "Inversión total: %.2f %s".format(inversionTotal, productoActual.currency)
                textViewInversionExistente.text = "Inversión existente: %.2f %s".format(inversionExistente, productoActual.currency)
                textViewGananciaPotencial.text = "Ganancia potencial: %.2f %s".format(gananciaPotencial, productoActual.currency)
                textViewTotalVendido.text = "Total vendido: %.2f %s".format(totalVendido, productoActual.currency)
            }
        } }
        recyclerViewVentas.layoutManager = LinearLayoutManager(this)
        recyclerViewVentas.adapter = ventasAdapter

        fun actualizarInformacion() {
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val ventas = withContext(Dispatchers.IO) { db.ventaProductoDao().getByProducto(productoActual.id) }
                ventasList.clear()
                ventasList.addAll(ventas)
                ventasAdapter.notifyDataSetChanged()

                // Calcular inversión total (cantidad total comprada × precio compra actual)
                val inversionTotal = productoActual.cantidadTotalComprada * productoActual.purchasePrice
                // Calcular inversión existente (stock actual × precio de compra)
                val inversionExistente = productoActual.quantity * productoActual.purchasePrice
                // Calcular ganancia potencial (stock actual × (precio venta - precio compra))
                val gananciaPotencial = productoActual.quantity * (productoActual.sellingPrice - productoActual.purchasePrice)
                // Calcular total vendido (costo de lo vendido, sin ganancia)
                val totalCantidadVendida = withContext(Dispatchers.IO) {
                    db.ventaProductoDao().getTotalCantidadVendidaByProducto(productoActual.id) ?: 0.0
                }
                val totalVendido = totalCantidadVendida * productoActual.purchasePrice
                
                textViewCantidadTotalComprada.text = "Cantidad total comprada: %.2f %s".format(productoActual.cantidadTotalComprada, productoActual.unit)
                textViewInversionTotal.text = "Inversión total: %.2f %s".format(inversionTotal, productoActual.currency)
                textViewInversionExistente.text = "Inversión existente: %.2f %s".format(inversionExistente, productoActual.currency)
                textViewGananciaPotencial.text = "Ganancia potencial: %.2f %s".format(gananciaPotencial, productoActual.currency)
                textViewTotalVendido.text = "Total vendido: %.2f %s".format(totalVendido, productoActual.currency)
            }
        }

        actualizarInformacion()

        // Configurar DatePicker
        fun showDatePicker() {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = selectedDate
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

            val datePickerDialog = android.app.DatePickerDialog(
                this@MainActivity,
                { _, selectedYear, selectedMonth, selectedDay ->
                    calendar.set(selectedYear, selectedMonth, selectedDay)
                    selectedDate = calendar.time
                    editTextFechaVenta.setText(dateFormat.format(selectedDate))
                },
                year,
                month,
                day
            )
            datePickerDialog.show()
        }

        editTextFechaVenta.setOnClickListener { showDatePicker() }
        buttonDatePickerVenta.setOnClickListener { showDatePicker() }

        // Configurar calculadora para precio de venta
        buttonCalculatorVenta.setOnClickListener {
            val calculator = CalculatorDialog(this@MainActivity)
            calculator.show { result ->
                editTextPrecioVenta.setText(decimalFormat.format(result))
            }
        }

        val alertDialog = AlertDialog.Builder(this)
            .setTitle("Detalle de ${producto.name}")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()

        buttonRegistrarVenta.setOnClickListener {
            val cantidad = editTextCantidadVenta.text.toString().toDoubleOrNull() ?: 0.0
            val precioVenta = editTextPrecioVenta.text.toString().toDoubleOrNull()
            val nota = editTextNotaVenta.text.toString().trim().ifEmpty { null }

            if (cantidad <= 0.0) {
                Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (cantidad > productoActual.quantity) {
                Toast.makeText(this, "No hay suficiente stock (disponible: ${productoActual.quantity})", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val precioFinal = precioVenta ?: productoActual.sellingPrice
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val userId = withContext(Dispatchers.IO) { dataManager.getCurrentUser().email }

                // Calcular costo de venta y ganancia
                val costoVenta = cantidad * productoActual.purchasePrice
                val montoVenta = cantidad * precioFinal
                val ganancia = montoVenta - costoVenta

                // Registrar transacción en cuenta de venta si está configurada
                var transactionVentaId: Long? = null
                if (productoActual.cuentaVentaId != null) {
                    val transVenta = Transaction(
                        userId = userId,
                        amount = costoVenta,
                        description = "Venta de ${productoActual.name} (${cantidad} ${productoActual.unit})",
                        type = "Ingreso",
                        category = "Venta Producto",
                        accountId = productoActual.cuentaVentaId,
                        date = selectedDate
                    )
                    transactionVentaId = withContext(Dispatchers.IO) { db.transactionDao().insert(transVenta) }
                }

                // Registrar transacción en cuenta de ganancia si está configurada
                if (productoActual.cuentaGananciaId != null) {
                    val transGanancia = Transaction(
                        userId = userId,
                        amount = ganancia,
                        description = "Ganancia de venta de ${productoActual.name} (${cantidad} ${productoActual.unit})",
                        type = "Ingreso",
                        category = "Ganancia Producto",
                        accountId = productoActual.cuentaGananciaId,
                        date = selectedDate
                    )
                    withContext(Dispatchers.IO) { db.transactionDao().insert(transGanancia) }
                }

                // Registrar venta con la fecha seleccionada
                withContext(Dispatchers.IO) {
                    db.ventaProductoDao().insert(
                        VentaProducto(
                            productoId = productoActual.id,
                            cantidad = cantidad,
                            precioVenta = precioFinal,
                            moneda = productoActual.currency,
                            nota = nota,
                            fecha = selectedDate,
                            transactionId = transactionVentaId
                        )
                    )
                }

                // Actualizar stock del producto
                val nuevoStock = productoActual.quantity - cantidad
                val productoActualizado = productoActual.copy(quantity = nuevoStock)
                withContext(Dispatchers.IO) { db.productoDao().update(productoActualizado) }

                // Actualizar la variable local con el producto actualizado
                productoActual = productoActualizado

                editTextCantidadVenta.text.clear()
                editTextPrecioVenta.text.clear()
                editTextNotaVenta.text.clear()
                editTextFechaVenta.setText(dateFormat.format(java.util.Date()))
                selectedDate = java.util.Date()
                actualizarInformacion()
                actualizarStockEnDialogo()
                Toast.makeText(this@MainActivity, "Venta registrada. Stock actual: ${productoActual.quantity}", Toast.LENGTH_SHORT).show()
                loadBalance()
                // Notificar cambios en productos y transacciones
                dataUpdateObserver.notifyProductosChanged()
                dataUpdateObserver.notifyTransactionsChanged()
            }
        }

        buttonEliminarProducto.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Seguro que deseas eliminar este producto? Esta acción no se puede deshacer.")
                .setPositiveButton("Sí") { _, _ ->
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(applicationContext)
                        withContext(Dispatchers.IO) {
                            // Eliminar transacciones asociadas
                            val ventas = db.ventaProductoDao().getByProducto(producto.id)
                            for (venta in ventas) {
                                venta.transactionId?.let { db.transactionDao().deleteById(it) }
                            }
                            // Eliminar ventas
                            db.ventaProductoDao().deleteAllByProducto(producto.id)
                            // Eliminar producto
                            db.productoDao().delete(producto)
                        }
                        Toast.makeText(this@MainActivity, "Producto eliminado", Toast.LENGTH_SHORT).show()
                        loadBalance()
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }

        buttonReiniciarContadores.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reiniciar contadores")
                .setMessage("¿Seguro que deseas reiniciar los contadores de este producto? Esto pondrá el stock y la cantidad total comprada en 0, pero NO eliminará el historial de ventas. Se guardará un registro del ciclo actual para referencia futura. Útil para empezar un nuevo ciclo de inversión.")
                .setPositiveButton("Sí") { _, _ ->
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(applicationContext)
                        withContext(Dispatchers.IO) {
                            // Guardar corte del ciclo actual
                            val corte = CorteProducto(
                                productoId = productoActual.id,
                                fechaCorte = java.util.Date(),
                                cantidadTotalComprada = productoActual.cantidadTotalComprada,
                                purchasePrice = productoActual.purchasePrice,
                                sellingPrice = productoActual.sellingPrice,
                                precioVentaDefinido = productoActual.precioVentaDefinido,
                                currency = productoActual.currency,
                                unit = productoActual.unit
                            )
                            db.corteProductoDao().insert(corte)

                            // Reiniciar contadores del producto
                            val productoReiniciado = productoActual.copy(
                                quantity = 0.0,
                                cantidadTotalComprada = 0.0
                            )
                            db.productoDao().update(productoReiniciado)
                            productoActual = productoReiniciado
                        }
                        actualizarStockEnDialogo()
                        actualizarInformacion()
                        Toast.makeText(this@MainActivity, "Contadores reiniciados. Nuevo ciclo iniciado. Corte guardado.", Toast.LENGTH_SHORT).show()
                        loadBalance()
                    }
                }
                .setNegativeButton("No", null)
                .show()
        }

        buttonIncrementarStock.setOnClickListener {
            mostrarDialogoIncrementarStock(producto)
        }

        buttonVerCortes.setOnClickListener {
            mostrarHistorialCortes(productoActual)
        }

        alertDialog.show()
    }

    private fun mostrarHistorialCortes(producto: Producto) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val cortes = withContext(Dispatchers.IO) { db.corteProductoDao().getByProducto(producto.id) }

            if (cortes.isEmpty()) {
                Toast.makeText(this@MainActivity, "No hay cortes registrados para este producto", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val cortesTexto = cortes.mapIndexed { index, corte ->
                val fechaCorte = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(corte.fechaCorte)
                val fechaInicio = if (index < cortes.size - 1) {
                    java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(cortes[index + 1].fechaCorte)
                } else {
                    "Inicio"
                }

                // Obtener ventas de este ciclo
                // El ciclo es DESDE el corte siguiente HASTA el corte actual
                val fechaDesde = if (index < cortes.size - 1) cortes[index + 1].fechaCorte else java.util.Date(0)
                val fechaHasta = corte.fechaCorte
                val todasLasVentas = withContext(Dispatchers.IO) {
                    db.ventaProductoDao().getByProducto(producto.id)
                }
                val ventasCiclo = todasLasVentas.filter { venta ->
                    venta.fecha >= fechaDesde && venta.fecha < fechaHasta
                }

                val totalVentasCiclo = ventasCiclo.sumOf { it.cantidad * it.precioVenta }
                val totalCantidadCiclo = ventasCiclo.sumOf { it.cantidad }
                val costoVentasCiclo = totalCantidadCiclo * corte.purchasePrice
                val gananciaCiclo = totalVentasCiclo - costoVentasCiclo

                """
                === CORTE #$${cortes.size - index} ===
                Fecha corte: $fechaCorte
                Período: $fechaInicio - $fechaCorte
                ------------------------
                DATOS DEL PRODUCTO EN EL CORTE
                Cantidad total comprada: ${String.format("%.2f", corte.cantidadTotalComprada)} ${corte.unit}
                Precio compra: ${String.format("%.2f", corte.purchasePrice)} ${corte.currency}
                Precio venta: ${String.format("%.2f", corte.sellingPrice)} ${corte.currency}
                Precio planeado: ${String.format("%.2f", corte.precioVentaDefinido)} ${corte.currency}
                ------------------------
                RESULTADOS DEL CICLO
                Total vendido: ${String.format("%.2f", totalVentasCiclo)} ${corte.currency}
                Cantidad vendida: ${String.format("%.2f", totalCantidadCiclo)} ${corte.unit}
                Costo de ventas: ${String.format("%.2f", costoVentasCiclo)} ${corte.currency}
                Ganancia: ${String.format("%.2f", gananciaCiclo)} ${corte.currency}
                Ventas registradas: ${ventasCiclo.size}
                """.trimIndent()
            }.joinToString("\n\n")

            AlertDialog.Builder(this@MainActivity)
                .setTitle("Historial de Cortes - ${producto.name}")
                .setMessage(cortesTexto)
                .setPositiveButton("Cerrar", null)
                .show()
        }
    }

    private fun mostrarDialogoIncrementarStock(producto: Producto) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_incrementar_stock, null)
        val textViewInfoProducto = dialogView.findViewById<TextView>(R.id.textViewInfoProducto)
        val editTextCantidadAdicional = dialogView.findViewById<EditText>(R.id.editTextCantidadAdicional)
        val editTextPrecioCompraAdicional = dialogView.findViewById<EditText>(R.id.editTextPrecioCompraAdicional)
        val editTextPrecioVentaAdicional = dialogView.findViewById<EditText>(R.id.editTextPrecioVentaAdicional)
        val buttonCalculatorCompra = dialogView.findViewById<Button>(R.id.buttonCalculatorCompra)
        val buttonCalculatorVenta = dialogView.findViewById<Button>(R.id.buttonCalculatorVenta)
        val spinnerCuenta = dialogView.findViewById<Spinner>(R.id.spinnerCuenta)
        val decimalFormat = DecimalFormat("#.##")

        textViewInfoProducto.text = "Producto: ${producto.name} - Stock actual: %.2f %s".format(producto.quantity, producto.unit)

        // Cargar cuentas y poblar el spinner
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val cuentasNombres = mutableListOf("Sin cuenta")
            cuentasNombres.addAll(cuentas.map { it.name })
            val adapterCuentas = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, cuentasNombres)
            spinnerCuenta.adapter = adapterCuentas
        }

        // Configurar calculadoras
        buttonCalculatorCompra.setOnClickListener {
            val calculator = CalculatorDialog(this@MainActivity)
            calculator.show { result ->
                editTextPrecioCompraAdicional.setText(decimalFormat.format(result))
            }
        }

        buttonCalculatorVenta.setOnClickListener {
            val calculator = CalculatorDialog(this@MainActivity)
            calculator.show { result ->
                editTextPrecioVentaAdicional.setText(decimalFormat.format(result))
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Incrementar stock")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val cantidadAdicional = editTextCantidadAdicional.text.toString().toDoubleOrNull() ?: 0.0
                val precioCompraAdicional = editTextPrecioCompraAdicional.text.toString().toDoubleOrNull()
                val precioVentaAdicional = editTextPrecioVentaAdicional.text.toString().toDoubleOrNull()
                val posCuenta = spinnerCuenta.selectedItemPosition

                if (cantidadAdicional <= 0) {
                    Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (precioCompraAdicional == null || precioCompraAdicional <= 0.0) {
                    Toast.makeText(this, "Precio compra inválido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (precioVentaAdicional == null || precioVentaAdicional <= 0.0) {
                    Toast.makeText(this, "Precio venta inválido", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val stockActual = producto.quantity
                    val nuevoStock = stockActual + cantidadAdicional

                    // Promedio ponderado de precio de compra
                    val nuevoPrecioCompra = ((stockActual * producto.purchasePrice) + (cantidadAdicional * precioCompraAdicional)) / nuevoStock

                    // Promedio ponderado de precio de venta
                    val nuevoPrecioVenta = ((stockActual * producto.sellingPrice) + (cantidadAdicional * precioVentaAdicional)) / nuevoStock

                    val productoActualizado = producto.copy(
                        purchasePrice = nuevoPrecioCompra,
                        sellingPrice = nuevoPrecioVenta,
                        quantity = nuevoStock,
                        precioVentaDefinido = nuevoPrecioVenta,
                        cantidadTotalComprada = producto.cantidadTotalComprada + cantidadAdicional
                    )

                    withContext(Dispatchers.IO) { db.productoDao().update(productoActualizado) }

                    // Registrar gasto del stock adicional si se seleccionó cuenta
                    if (posCuenta > 0) {
                        val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                        val cuentaSeleccionada = cuentas[posCuenta - 1]
                        try {
                            val userId = withContext(Dispatchers.IO) {
                            dataManager.getCurrentUser().email
                        }
                            val inversionAdicional = cantidadAdicional * precioCompraAdicional
                            val trans = Transaction(
                                userId = userId,
                                amount = -inversionAdicional,
                                description = "Compra adicional de ${producto.name} ($cantidadAdicional ${producto.unit})",
                                type = "Gasto",
                                category = "Compra Producto",
                                accountId = cuentaSeleccionada.id,
                                date = Date()
                            )
                            withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Error al registrar el gasto: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }

                    Toast.makeText(this@MainActivity, "Stock incrementado", Toast.LENGTH_SHORT).show()
                    loadBalance()
                    dataUpdateObserver.notifyProductosChanged()
                    dataUpdateObserver.notifyTransactionsChanged()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarVenta(venta: VentaProducto, producto: Producto, onVentaEliminada: (Producto) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar venta")
            .setMessage("¿Seguro que deseas eliminar esta venta?")
            .setPositiveButton("Sí") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) {
                        // Eliminar transacción asociada
                        venta.transactionId?.let { db.transactionDao().deleteById(it) }
                        // Eliminar venta
                        db.ventaProductoDao().delete(venta)
                        // Restaurar stock
                        val nuevoStock = producto.quantity + venta.cantidad
                        val productoActualizado = producto.copy(quantity = nuevoStock)
                        db.productoDao().update(productoActualizado)
                        
                        // Llamar al callback con el producto actualizado
                        withContext(Dispatchers.Main) {
                            onVentaEliminada(productoActualizado)
                        }
                    }
                    Toast.makeText(this@MainActivity, "Venta eliminada. Stock actual: ${producto.quantity + venta.cantidad}", Toast.LENGTH_SHORT).show()
                    loadBalance()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
    
    /**
     * Carga las personas relacionadas (deudores y acreedores) con lógica de conversión
     */
    private suspend fun loadPersonas() {
        loadPersonasMutex.withLock {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val defaultCurrency = "CUP"

                // ===== DEUDORES =====
                val deudores = withContext(Dispatchers.IO) {
                    db.personaRelacionadaDao().getByTipoAndActiva("DEUDOR")
                }
                deudoresList.clear()
                var totalDeudores = 0.0

                // Mapa para llevar el control de los excedentes por nombre
                val excedentesPorNombre = mutableMapOf<String, Double>()
                val deudoresAActualizar = mutableListOf<PersonaRelacionada>()

                for (d in deudores) {
                    val abonos = withContext(Dispatchers.IO) {
                        db.abonoPersonaDao().getByPersona(d.id)
                    }
                    var pendiente = d.monto

                    for (ab in abonos) {
                        val abRate = if (ab.moneda == d.moneda) {
                            1.0
                        } else {
                            withContext(Dispatchers.IO) {
                                db.currencyRateDao().getSmartRate(ab.moneda, d.moneda) ?: 1.0
                            }
                        }
                        pendiente += ab.monto * abRate
                    }

                    android.util.Log.d("MainActivity", "Deudor: ${d.nombre}, monto inicial: ${d.monto}, pendiente: $pendiente, abonos: ${abonos.size}")

                    when {
                        pendiente < -0.01 && d.monto > 0.01 -> {
                            // 🔴 Debe pasar a ACREEDOR (pagó más de lo que debía)
                            // Solo procesar si el monto inicial es > 0 (evitar reprocesar deudores ya actualizados a 0)
                            val excedente = kotlin.math.abs(pendiente)
                            android.util.Log.d("MainActivity", "  -> Debe pasar a ACREEDOR con excedente: $excedente")
                            
                            // Guardar el excedente para este nombre
                            excedentesPorNombre[d.nombre] = excedente
                            deudoresAActualizar.add(d)
                            
                            // ❌ NO lo agregamos a deudoresList (no debe aparecer en pantalla principal)
                        }
                        pendiente > 0.01 -> {
                            // 🟢 Se mantiene como deudor en pantalla principal
                            deudoresList.add(d)
                            val rate = if (d.moneda == defaultCurrency) {
                                1.0
                            } else {
                                withContext(Dispatchers.IO) {
                                    db.currencyRateDao().getSmartRate(d.moneda, defaultCurrency) ?: 1.0
                                }
                            }
                            totalDeudores += pendiente * rate
                            android.util.Log.d("MainActivity", "  -> Se mantiene como deudor (saldo positivo)")
                        }
                        else -> {
                            // ⚪ Saldo cero - NO aparece en pantalla principal
                            android.util.Log.d("MainActivity", "  -> Saldo cero, NO aparece en pantalla principal")
                        }
                    }
                }

                // 🔄 Actualizar acreedores con los excedentes
                withContext(Dispatchers.IO) {
                    for ((nombre, excedente) in excedentesPorNombre) {
                        android.util.Log.d("MainActivity", "Procesando excedente para $nombre: $excedente")
                        
                        // Buscar si ya existe un acreedor con este nombre
                        val acreedorExistente = db.personaRelacionadaDao().getByNombreAndTipo(nombre, "ACREEDOR")
                        
                        if (acreedorExistente != null) {
                            // ✅ Actualizar el acreedor existente REEMPLAZANDO el monto por el excedente
                            // (NO sumando, porque el excedente ya incluye todo lo pagado de más)
                            val nuevoMonto = excedente
                            db.personaRelacionadaDao().update(
                                acreedorExistente.copy(
                                    monto = nuevoMonto,
                                    fecha = Date(),
                                    activa = true
                                )
                            )
                            android.util.Log.d("MainActivity", "  -> Acreedor existente actualizado: ${acreedorExistente.nombre}, nuevo monto: $nuevoMonto")
                        } else {
                            // Crear NUEVO acreedor
                            val nuevoAcreedor = PersonaRelacionada(
                                nombre = nombre,
                                monto = excedente,
                                moneda = "CUP",
                                tipo = "ACREEDOR",
                                descripcion = "Excedente por pago de deuda",
                                fecha = Date(),
                                activa = true
                            )
                            val nuevoId = db.personaRelacionadaDao().insert(nuevoAcreedor)
                            android.util.Log.d("MainActivity", "  -> Nuevo acreedor creado con ID: $nuevoId")
                        }
                    }
                    
                    // Actualizar deudores a monto 0 (conservar historial)
                    for (d in deudoresAActualizar) {
                        db.personaRelacionadaDao().update(
                            d.copy(monto = 0.0, fecha = Date(), activa = true)
                        )
                        android.util.Log.d("MainActivity", "  -> Deudor ${d.nombre} actualizado con monto 0 (conserva historial)")
                    }
                }

                deudoresAdapter.notifyDataSetChanged()
                val textViewTotalDeudores = findViewById<TextView>(R.id.textViewTotalDeudores)
                textViewTotalDeudores.text = "Total: %.2f %s".format(totalDeudores, defaultCurrency)

                // ===== ACREEDORES =====
                val acreedores = withContext(Dispatchers.IO) {
                    db.personaRelacionadaDao().getByTipoAndActiva("ACREEDOR")
                }
                acreedoresList.clear()
                var totalAcreedores = 0.0

                // Mapa para llevar el control de los excedentes de acreedores
                val excedentesAcreedores = mutableMapOf<String, Double>()
                val acreedoresAActualizar = mutableListOf<PersonaRelacionada>()

                for (a in acreedores) {
                    val abonos = withContext(Dispatchers.IO) {
                        db.abonoPersonaDao().getByPersona(a.id)
                    }
                    var pendiente = a.monto

                    for (ab in abonos) {
                        val abRate = if (ab.moneda == a.moneda) {
                            1.0
                        } else {
                            withContext(Dispatchers.IO) {
                                db.currencyRateDao().getSmartRate(ab.moneda, a.moneda) ?: 1.0
                            }
                        }
                        pendiente += ab.monto * abRate
                    }

                    when {
                        pendiente < -0.01 && a.monto > 0.01 -> {
                            // 🔴 Debe pasar a DEUDOR (pagó más de lo que debía)
                            // Solo procesar si el monto inicial es > 0 (evitar reprocesar acreedores ya actualizados a 0)
                            val excedente = kotlin.math.abs(pendiente)
                            android.util.Log.d("MainActivity", "Acreedor ${a.nombre} debe pasar a DEUDOR con excedente: $excedente")
                            excedentesAcreedores[a.nombre] = excedente
                            acreedoresAActualizar.add(a)
                        }
                        pendiente > 0.01 -> {
                            // 🟢 Se mantiene como acreedor en pantalla principal
                            acreedoresList.add(a)
                            val rate = if (a.moneda == defaultCurrency) {
                                1.0
                            } else {
                                withContext(Dispatchers.IO) {
                                    db.currencyRateDao().getSmartRate(a.moneda, defaultCurrency) ?: 1.0
                                }
                            }
                            totalAcreedores += pendiente * rate
                        }
                        else -> {
                            // ⚪ Saldo cero - NO aparece en pantalla principal
                            android.util.Log.d("MainActivity", "Acreedor ${a.nombre} saldo cero")
                        }
                    }
                }

                // 🔄 Actualizar deudores con los excedentes de acreedores
                withContext(Dispatchers.IO) {
                    for ((nombre, excedente) in excedentesAcreedores) {
                        android.util.Log.d("MainActivity", "Procesando excedente de acreedor para $nombre: $excedente")
                        
                        val deudorExistente = db.personaRelacionadaDao().getByNombreAndTipo(nombre, "DEUDOR")
                        
                        if (deudorExistente != null) {
                            // ✅ Actualizar el deudor existente REEMPLAZANDO el monto
                            db.personaRelacionadaDao().update(
                                deudorExistente.copy(
                                    monto = excedente,
                                    fecha = Date(),
                                    activa = true
                                )
                            )
                            android.util.Log.d("MainActivity", "  -> Deudor existente actualizado: ${deudorExistente.nombre}, nuevo monto: $excedente")
                        } else {
                            val nuevoDeudor = PersonaRelacionada(
                                nombre = nombre,
                                monto = excedente,
                                moneda = "CUP",
                                tipo = "DEUDOR",
                                descripcion = "Excedente por pago de acreencia",
                                fecha = Date(),
                                activa = true
                            )
                            val nuevoId = db.personaRelacionadaDao().insert(nuevoDeudor)
                            android.util.Log.d("MainActivity", "  -> Nuevo deudor creado con ID: $nuevoId")
                        }
                    }
                    
                    // Actualizar acreedores a monto 0 (conservar historial)
                    for (a in acreedoresAActualizar) {
                        db.personaRelacionadaDao().update(
                            a.copy(monto = 0.0, fecha = Date(), activa = true)
                        )
                        android.util.Log.d("MainActivity", "  -> Acreedor ${a.nombre} actualizado con monto 0 (conserva historial)")
                    }
                }

                acreedoresAdapter.notifyDataSetChanged()
                val textViewTotalAcreedores = findViewById<TextView>(R.id.textViewTotalAcreedores)
                textViewTotalAcreedores.text = "Total: %.2f %s".format(totalAcreedores, defaultCurrency)

            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error cargando personas", e)
            }
        }
    }
    
    /**
     * Carga los productos por separado
     */
    private fun loadProductos() {
        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val productos = withContext(Dispatchers.IO) { db.productoDao().getAll() }
                productosList.clear()
                productosList.addAll(productos)

                // Calcular ganancias por producto y total vendido por producto
                val gananciasPorProducto = mutableMapOf<Long, Double>()
                val totalVendidoPorProducto = mutableMapOf<Long, Double>()
                for (producto in productos) {
                    val totalVentas = withContext(Dispatchers.IO) {
                        db.ventaProductoDao().getTotalVentasByProducto(producto.id) ?: 0.0
                    }
                    val totalCantidadVendida = withContext(Dispatchers.IO) {
                        db.ventaProductoDao().getTotalCantidadVendidaByProducto(producto.id) ?: 0.0
                    }
                    val costoVentas = totalCantidadVendida * producto.purchasePrice
                    val ganancia = totalVentas - costoVentas
                    gananciasPorProducto[producto.id] = ganancia
                    // Total vendido es el costo de lo vendido (sin ganancia)
                    totalVendidoPorProducto[producto.id] = costoVentas
                }

                // Actualizar adapter con las ganancias calculadas
                productosAdapter = ProductosResumenAdapter(productosList, gananciasPorProducto, totalVendidoPorProducto)
                productosRecyclerView.adapter = productosAdapter
                productosAdapter.onItemClick = { producto -> mostrarDetalleProducto(producto) }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error cargando productos", e)
            }
        }
    }
    
    /**
     * Actualiza la información del header del menú lateral
     */
    private fun updateMenuHeader() {
        try {
            val headerView = navigationView.getHeaderView(0)
            val textViewUserName = headerView.findViewById<TextView>(R.id.textViewUserName)
            
            // Actualizar nombre de usuario si está disponible
            lifecycleScope.launch {
                try {
                    val user = dataManager.getCurrentUser()
                    textViewUserName?.text = user.name ?: user.email
                } catch (e: Exception) {
                    textViewUserName?.text = "Usuario"
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error actualizando header del menú", e)
        }
    }
}

class PersonasResumenAdapter(private val items: List<PersonaRelacionada>) : RecyclerView.Adapter<PersonasResumenAdapter.ViewHolder>() {
    var onItemClick: ((PersonaRelacionada) -> Unit)? = null
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val db = AppDatabase.getInstance(context)
        holder.text2.text = item.descripcion ?: ""
        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
        // Calcular y mostrar pendiente
        (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
            val abonos = withContext(Dispatchers.IO) { db.abonoPersonaDao().getByPersona(item.id) }
            var pendiente = item.monto
            for (ab in abonos) {
                val rate = if (ab.moneda == item.moneda) {
                    1.0
                } else {
                    withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(ab.moneda, item.moneda) ?: 1.0
                    }
                }
                pendiente += ab.monto * rate
            }
            holder.text1.text = "${item.nombre}: %.2f ${item.moneda} pendiente".format(pendiente)
        }
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text1: TextView = view.findViewById(android.R.id.text1)
        val text2: TextView = view.findViewById(android.R.id.text2)
    }
}

class AbonosAdapter(private val items: List<AbonoPersona>, val onDelete: (AbonoPersona) -> Unit) : RecyclerView.Adapter<AbonosAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_abono, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.monto.text = "Abono: %.2f %s".format(item.monto, item.moneda)
        holder.fecha.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(item.fecha)
        holder.nota.text = item.nota ?: ""
        holder.deleteButton.setOnClickListener { onDelete(item) }
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val monto: TextView = view.findViewById(R.id.textViewAbonoMonto)
        val fecha: TextView = view.findViewById(R.id.textViewAbonoFecha)
        val nota: TextView = view.findViewById(R.id.textViewAbonoNota)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteAbono)
    }
}

class ProductosResumenAdapter(
    private val items: List<Producto>,
    private val gananciasPorProducto: Map<Long, Double> = emptyMap(),
    private val totalVendidoPorProducto: Map<Long, Double> = emptyMap()
) : RecyclerView.Adapter<ProductosResumenAdapter.ViewHolder>() {
    var onItemClick: ((Producto) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_producto, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.nombre.text = item.name
        holder.cantidad.text = "${item.quantity} ${item.unit}"

        // Combinar compra y venta en un solo TextView
        holder.precioCompra.text = "C: %.2f %s | V: %.2f %s".format(item.purchasePrice, item.currency, item.sellingPrice, item.currency)

        // Mostrar ganancia calculada y total vendido
        val ganancia = gananciasPorProducto[item.id] ?: 0.0
        val totalVendido = totalVendidoPorProducto[item.id] ?: 0.0
        holder.ganancia.text = "Gan: %.2f %s | Ven: %.2f %s".format(ganancia, item.currency, totalVendido, item.currency)
        holder.ganancia.setTextColor(
            if (ganancia >= 0) android.graphics.Color.parseColor("#9C27B0")
            else android.graphics.Color.RED
        )

        holder.deleteButton.visibility = View.GONE // Solo visualización en principal
        holder.venderButton.visibility = View.GONE // Solo visualización en principal
        holder.editButton.visibility = View.GONE // Solo visualización en principal

        holder.itemView.setOnClickListener { onItemClick?.invoke(item) }
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombre: TextView = view.findViewById(R.id.textViewNombreProducto)
        val cantidad: TextView = view.findViewById(R.id.textViewCantidadProducto)
        val precioCompra: TextView = view.findViewById(R.id.textViewPrecioCompra)
        val ganancia: TextView = view.findViewById(R.id.textViewGanancia)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteProducto)
        val venderButton: Button = view.findViewById(R.id.buttonVenderProducto)
        val editButton: Button = view.findViewById(R.id.buttonEditProducto)
    }
}

class VentasAdapter(private val items: List<VentaProducto>, val onDelete: (VentaProducto) -> Unit) : RecyclerView.Adapter<VentasAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_venta, parent, false)
        return ViewHolder(view)
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.cantidad.text = "Cantidad: %.2f".format(item.cantidad)
        holder.precio.text = "Precio: %.2f %s".format(item.precioVenta, item.moneda)
        holder.fecha.text = "Fecha: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(item.fecha)}"
        holder.nota.text = item.nota ?: "Sin nota"
        holder.nota.visibility = if (item.nota.isNullOrBlank()) View.GONE else View.VISIBLE
        holder.deleteButton.setOnClickListener { onDelete(item) }
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cantidad: TextView = view.findViewById(R.id.textViewVentaCantidad)
        val precio: TextView = view.findViewById(R.id.textViewVentaPrecio)
        val fecha: TextView = view.findViewById(R.id.textViewVentaFecha)
        val nota: TextView = view.findViewById(R.id.textViewVentaNota)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteVenta)
    }
}

private suspend fun MainActivity.corregirCuentasConExchangeRateIncorrecto() {
    try {
        val db = cu.rge.cartera.data.AppDatabase.getInstance(applicationContext)
        val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
        
        for (cuenta in cuentas) {
            // Si la moneda es CUP, el exchangeRate debe ser 1.0
            if (cuenta.currency == "CUP" && cuenta.exchangeRate != 1.0) {
                android.util.Log.d("MainActivity", "Corrigiendo exchangeRate de ${cuenta.name} de ${cuenta.exchangeRate} a 1.0")
                val corregida = cuenta.copy(exchangeRate = 1.0)
                withContext(Dispatchers.IO) { db.accountDao().update(corregida) }
            }
            
            // Si la moneda es MLC, el exchangeRate debe ser 1.0 (equivalente a CUP)
            if (cuenta.currency == "MLC" && cuenta.exchangeRate != 1.0) {
                android.util.Log.d("MainActivity", "Corrigiendo exchangeRate de ${cuenta.name} de ${cuenta.exchangeRate} a 1.0")
                val corregida = cuenta.copy(exchangeRate = 1.0)
                withContext(Dispatchers.IO) { db.accountDao().update(corregida) }
            }
            
            // Si la moneda es USD, el exchangeRate debe ser la tasa CUP→USD
            if (cuenta.currency == "USD") {
                val tasaCorrecta = withContext(Dispatchers.IO) {
                    db.currencyRateDao().getSmartRate("CUP", "USD") ?: 1.0
                }
                if (kotlin.math.abs(cuenta.exchangeRate - tasaCorrecta) > 0.0001) {
                    android.util.Log.d("MainActivity", "Corrigiendo exchangeRate de ${cuenta.name} de ${cuenta.exchangeRate} a $tasaCorrecta")
                    val corregida = cuenta.copy(exchangeRate = tasaCorrecta)
                    withContext(Dispatchers.IO) { db.accountDao().update(corregida) }
                }
            }
            
            // Si la moneda es EUR, el exchangeRate debe ser la tasa CUP→EUR
            if (cuenta.currency == "EUR") {
                val tasaCorrecta = withContext(Dispatchers.IO) {
                    db.currencyRateDao().getSmartRate("CUP", "EUR") ?: 1.0
                }
                if (kotlin.math.abs(cuenta.exchangeRate - tasaCorrecta) > 0.0001) {
                    android.util.Log.d("MainActivity", "Corrigiendo exchangeRate de ${cuenta.name} de ${cuenta.exchangeRate} a $tasaCorrecta")
                    val corregida = cuenta.copy(exchangeRate = tasaCorrecta)
                    withContext(Dispatchers.IO) { db.accountDao().update(corregida) }
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("MainActivity", "Error corrigiendo cuentas", e)
    }
}