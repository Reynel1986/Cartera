package cu.rge.cartera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import java.text.DecimalFormat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Producto
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.data.model.Transaction
import cu.rge.cartera.data.model.VentaProducto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import androidx.lifecycle.lifecycleScope
import java.util.Date

class ProductosActivity : AppCompatActivity() {
    private lateinit var adapter: ProductosAdapter
    private val productos = mutableListOf<Producto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_productos)

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewProductos)
        adapter = ProductosAdapter(productos,
            onEdit = { producto -> mostrarDialogoProducto(producto) },
            onDelete = { producto -> eliminarProducto(producto) },
            onVender = { producto -> venderProducto(producto) },
            onVerDetalle = { producto -> mostrarDetalleProducto(producto) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAgregarProducto).setOnClickListener {
            mostrarDialogoProducto(null)
        }

        cargarProductos()
    }

    private fun cargarProductos() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val lista = withContext(Dispatchers.IO) { db.productoDao().getAll() }
            productos.clear()
            productos.addAll(lista)
            adapter.notifyDataSetChanged()
        }
    }

    private fun mostrarDialogoProducto(producto: Producto?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_producto, null)
        val editTextNombre = dialogView.findViewById<EditText>(R.id.editTextNombreProducto)
        val editTextPrecioCompra = dialogView.findViewById<EditText>(R.id.editTextPrecioCompra)
        val editTextPrecioVentaDefinido = dialogView.findViewById<EditText>(R.id.editTextPrecioVentaDefinido)
        val editTextCantidad = dialogView.findViewById<EditText>(R.id.editTextCantidadProducto)
        val editTextUnidad = dialogView.findViewById<EditText>(R.id.editTextUnidadProducto)
        val spinnerMoneda = dialogView.findViewById<Spinner>(R.id.spinnerMonedaProducto)
        val spinnerCuenta = dialogView.findViewById<Spinner>(R.id.spinnerCuentaProducto)
        val spinnerCuentaGanancia = dialogView.findViewById<Spinner>(R.id.spinnerCuentaGanancia)
        val spinnerCuentaVenta = dialogView.findViewById<Spinner>(R.id.spinnerCuentaVenta)
        val buttonCalculatorCompra = dialogView.findViewById<Button>(R.id.buttonCalculatorCompra)
        val buttonCalculatorVentaDefinido = dialogView.findViewById<Button>(R.id.buttonCalculatorVentaDefinido)
        val textViewAgregarStock = dialogView.findViewById<TextView>(R.id.textViewAgregarStock)
        val layoutAgregarStock = dialogView.findViewById<LinearLayout>(R.id.layoutAgregarStock)
        val editTextCantidadAdicional = dialogView.findViewById<EditText>(R.id.editTextCantidadAdicional)
        val editTextPrecioCompraAdicional = dialogView.findViewById<EditText>(R.id.editTextPrecioCompraAdicional)
        val editTextPrecioVentaAdicional = dialogView.findViewById<EditText>(R.id.editTextPrecioVentaAdicional)
        val buttonCalculatorCompraAdicional = dialogView.findViewById<Button>(R.id.buttonCalculatorCompraAdicional)
        val buttonCalculatorVentaAdicional = dialogView.findViewById<Button>(R.id.buttonCalculatorVentaAdicional)
        val decimalFormat = DecimalFormat("#.##")

        // Mostrar sección de agregar stock solo al editar producto existente
        if (producto != null) {
            textViewAgregarStock.visibility = View.VISIBLE
            layoutAgregarStock.visibility = View.VISIBLE
        }

        val monedas = listOf("CUP", "MLC", "USD")
        spinnerMoneda.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monedas)

        // Cargar cuentas y poblar los spinners
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val cuentasNombres = mutableListOf("Sin cuenta")
            cuentasNombres.addAll(cuentas.map { it.name })
            val adapterCuentas = ArrayAdapter(this@ProductosActivity, android.R.layout.simple_spinner_dropdown_item, cuentasNombres)
            
            spinnerCuenta.adapter = adapterCuentas
            spinnerCuentaGanancia.adapter = adapterCuentas
            spinnerCuentaVenta.adapter = adapterCuentas
            
            // Seleccionar cuentas si se está editando
            if (producto != null) {
                val cuentaGananciaIndex = cuentas.indexOfFirst { it.id == producto.cuentaGananciaId }
                if (cuentaGananciaIndex >= 0) {
                    spinnerCuentaGanancia.setSelection(cuentaGananciaIndex + 1)
                }
                
                val cuentaVentaIndex = cuentas.indexOfFirst { it.id == producto.cuentaVentaId }
                if (cuentaVentaIndex >= 0) {
                    spinnerCuentaVenta.setSelection(cuentaVentaIndex + 1)
                }
            }
        }

        if (producto != null) {
            editTextNombre.setText(producto.name)
            editTextPrecioCompra.setText(producto.purchasePrice.toString())
            editTextPrecioVentaDefinido.setText(producto.precioVentaDefinido.toString())
            editTextCantidad.setText(producto.quantity.toString())
            editTextUnidad.setText(producto.unit)
            spinnerMoneda.setSelection(monedas.indexOf(producto.currency))
        }

        // Configurar botones de calculadora
        buttonCalculatorCompra.setOnClickListener {
            val calculator = CalculatorDialog(this@ProductosActivity)
            calculator.show { result ->
                editTextPrecioCompra.setText(decimalFormat.format(result))
            }
        }

        buttonCalculatorVentaDefinido.setOnClickListener {
            val calculator = CalculatorDialog(this@ProductosActivity)
            calculator.show { result ->
                editTextPrecioVentaDefinido.setText(decimalFormat.format(result))
            }
        }

        buttonCalculatorCompraAdicional.setOnClickListener {
            val calculator = CalculatorDialog(this@ProductosActivity)
            calculator.show { result ->
                editTextPrecioCompraAdicional.setText(decimalFormat.format(result))
            }
        }

        buttonCalculatorVentaAdicional.setOnClickListener {
            val calculator = CalculatorDialog(this@ProductosActivity)
            calculator.show { result ->
                editTextPrecioVentaAdicional.setText(decimalFormat.format(result))
            }
        }

        AlertDialog.Builder(this)
            .setTitle(if (producto == null) "Agregar producto" else "Editar producto")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nombre = editTextNombre.text.toString().trim()
                val precioCompra = editTextPrecioCompra.text.toString().toDoubleOrNull() ?: 0.0
                val precioVentaDefinido = editTextPrecioVentaDefinido.text.toString().toDoubleOrNull() ?: 0.0
                val cantidad = editTextCantidad.text.toString().toDoubleOrNull() ?: 0.0
                val unidad = editTextUnidad.text.toString().trim()
                val moneda = spinnerMoneda.selectedItem.toString()
                val posCuenta = spinnerCuenta.selectedItemPosition
                val posCuentaGanancia = spinnerCuentaGanancia.selectedItemPosition
                val posCuentaVenta = spinnerCuentaVenta.selectedItemPosition

                // Datos adicionales para agregar stock
                val cantidadAdicional = editTextCantidadAdicional.text.toString().toDoubleOrNull() ?: 0.0
                val precioCompraAdicional = editTextPrecioCompraAdicional.text.toString().toDoubleOrNull()
                val precioVentaAdicional = editTextPrecioVentaAdicional.text.toString().toDoubleOrNull()

                if (nombre.isEmpty() || precioCompra <= 0.0 || precioVentaDefinido <= 0.0 || cantidad < 0.0 || unidad.isEmpty()) {
                    Toast.makeText(this, "Datos inválidos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validar datos adicionales si se proporcionan
                if (cantidadAdicional > 0) {
                    if (precioCompraAdicional == null || precioCompraAdicional <= 0.0) {
                        Toast.makeText(this, "Precio compra adicional inválido", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (precioVentaAdicional == null || precioVentaAdicional <= 0.0) {
                        Toast.makeText(this, "Precio venta adicional inválido", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                    
                    val cuentaGananciaId = if (posCuentaGanancia > 0) cuentas[posCuentaGanancia - 1].id else null
                    val cuentaVentaId = if (posCuentaVenta > 0) cuentas[posCuentaVenta - 1].id else null

                    if (producto == null) {
                        // Nuevo producto
                        val nuevo = Producto(
                            id = 0,
                            name = nombre,
                            purchasePrice = precioCompra,
                            sellingPrice = precioVentaDefinido,
                            quantity = cantidad,
                            unit = unidad,
                            currency = moneda,
                            precioVentaDefinido = precioVentaDefinido,
                            cantidadTotalComprada = cantidad,
                            cuentaGananciaId = cuentaGananciaId,
                            cuentaVentaId = cuentaVentaId
                        )
                        withContext(Dispatchers.IO) { db.productoDao().insert(nuevo) }

                        // Registrar gasto si se seleccionó cuenta
                        if (posCuenta > 0) {
                            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                            val cuentaSeleccionada = cuentas[posCuenta - 1]
                            try {
                                val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                                val inversionTotal = cantidad * precioCompra
                                val trans = Transaction(
                                    userId = userId,
                                    amount = -inversionTotal,
                                    description = "Compra de $nombre ($cantidad $unidad)",
                                    type = "Gasto",
                                    category = "Compra Producto",
                                    accountId = cuentaSeleccionada.id,
                                    date = Date()
                                )
                                withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                            } catch (e: Exception) {
                                Toast.makeText(this@ProductosActivity, "Error al registrar el gasto: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        // Editar producto existente
                        var precioCompraFinal = precioCompra
                        var precioVentaFinal = precioVentaDefinido
                        var cantidadFinal = cantidad

                        // Si se agregó stock adicional, recalcular promedio ponderado
                        if (cantidadAdicional > 0) {
                            val stockActual = producto.quantity
                            val nuevoStock = stockActual + cantidadAdicional

                            // Promedio ponderado de precio de compra
                            precioCompraFinal = ((stockActual * producto.purchasePrice) + (cantidadAdicional * precioCompraAdicional!!)) / nuevoStock

                            // Promedio ponderado de precio de venta
                            precioVentaFinal = ((stockActual * producto.sellingPrice) + (cantidadAdicional * precioVentaAdicional!!)) / nuevoStock

                            cantidadFinal = nuevoStock

                            // Registrar gasto del stock adicional si se seleccionó cuenta
                            if (posCuenta > 0) {
                                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                                val cuentaSeleccionada = cuentas[posCuenta - 1]
                                try {
                                    val userId = withContext(Dispatchers.IO) { cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email }
                                    val inversionAdicional = cantidadAdicional * precioCompraAdicional
                                    val trans = Transaction(
                                        userId = userId,
                                        amount = -inversionAdicional,
                                        description = "Compra adicional de $nombre ($cantidadAdicional $unidad)",
                                        type = "Gasto",
                                        category = "Compra Producto",
                                        accountId = cuentaSeleccionada.id,
                                        date = Date()
                                    )
                                    withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                                } catch (e: Exception) {
                                    Toast.makeText(this@ProductosActivity, "Error al registrar el gasto adicional: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                        val actualizado = Producto(
                            id = producto.id,
                            name = nombre,
                            purchasePrice = precioCompraFinal,
                            sellingPrice = precioVentaFinal,
                            quantity = cantidadFinal,
                            unit = unidad,
                            currency = moneda,
                            precioVentaDefinido = precioVentaFinal,
                            cantidadTotalComprada = producto.cantidadTotalComprada,
                            cuentaGananciaId = cuentaGananciaId,
                            cuentaVentaId = cuentaVentaId
                        )
                        withContext(Dispatchers.IO) { db.productoDao().update(actualizado) }
                    }

                    cargarProductos()
                    Toast.makeText(this@ProductosActivity, "Producto guardado", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarProducto(producto: Producto) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar producto")
            .setMessage("¿Seguro que deseas eliminar ${producto.name}?")
            .setPositiveButton("Sí") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) { db.productoDao().delete(producto) }
                    cargarProductos()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun mostrarDetalleProducto(producto: Producto) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val ventas = withContext(Dispatchers.IO) { db.ventaProductoDao().getByProducto(producto.id) }
            val totalVentas = withContext(Dispatchers.IO) { db.ventaProductoDao().getTotalVentasByProducto(producto.id) } ?: 0.0
            val totalCantidadVendida = withContext(Dispatchers.IO) { db.ventaProductoDao().getTotalCantidadVendidaByProducto(producto.id) } ?: 0.0
            val promedioPrecioVenta = withContext(Dispatchers.IO) { db.ventaProductoDao().getPromedioPrecioVentaByProducto(producto.id) } ?: 0.0

            // Calcular inversión total (cantidad total comprada × precio compra)
            val inversionTotal = producto.cantidadTotalComprada * producto.purchasePrice
            // Calcular inversión existente (stock actual × precio compra)
            val inversionExistente = producto.quantity * producto.purchasePrice
            // Calcular ganancia potencial (stock actual × (precio venta - precio compra))
            val gananciaPotencial = producto.quantity * (producto.sellingPrice - producto.purchasePrice)
            val gananciaTotal = totalVentas - (totalCantidadVendida * producto.purchasePrice)
            val gananciaEsperada = (producto.quantity * (producto.precioVentaDefinido.takeIf { it > 0 } ?: producto.sellingPrice)) - inversionExistente
            val diferenciaGanancia = gananciaTotal - gananciaEsperada

            val detalle = """
                ${producto.name}
                ------------------------
                Stock actual: ${producto.quantity} ${producto.unit}
                Precio compra: ${producto.purchasePrice} ${producto.currency}
                Precio venta (último): ${producto.sellingPrice} ${producto.currency}
                Precio planeado: ${producto.precioVentaDefinido.takeIf { it > 0 } ?: producto.sellingPrice} ${producto.currency}
                
                INVERSIÓN Y STOCK
                ------------------------
                Cantidad total comprada: ${String.format("%.2f", producto.cantidadTotalComprada)} ${producto.unit}
                Inversión total: ${String.format("%.2f", inversionTotal)} ${producto.currency}
                Inversión existente: ${String.format("%.2f", inversionExistente)} ${producto.currency}
                Ganancia potencial: ${String.format("%.2f", gananciaPotencial)} ${producto.currency}
                
                VENTAS
                ------------------------
                Total vendido: ${String.format("%.2f", totalVentas)} ${producto.currency}
                Cantidad vendida: ${String.format("%.2f", totalCantidadVendida)} ${producto.unit}
                Precio promedio venta: ${String.format("%.2f", promedioPrecioVenta)} ${producto.currency}
                
                Ganancia real: ${String.format("%.2f", gananciaTotal)} ${producto.currency}
                Ganancia esperada: ${String.format("%.2f", gananciaEsperada)} ${producto.currency}
                Diferencia: ${String.format("%.2f", diferenciaGanancia)} ${producto.currency}
                
                Ventas registradas: ${ventas.size}
            """.trimIndent()

            AlertDialog.Builder(this@ProductosActivity)
                .setTitle("Detalle del Producto")
                .setMessage(detalle)
                .setPositiveButton("Ver ventas") { _, _ ->
                    mostrarListaVentas(producto, ventas)
                }
                .setNegativeButton("Cerrar", null)
                .show()
        }
    }

    private fun mostrarListaVentas(producto: Producto, ventas: List<VentaProducto>) {
        val ventasTexto = if (ventas.isEmpty()) {
            "No hay ventas registradas"
        } else {
            ventas.joinToString("\n\n") { venta ->
                val fecha = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(venta.fecha)
                val ganancia = (venta.precioVenta - producto.purchasePrice) * venta.cantidad
                """
                Fecha: $fecha
                Cantidad: ${venta.cantidad} ${producto.unit}
                Precio venta: ${venta.precioVenta} ${producto.currency}
                Total: ${String.format("%.2f", venta.cantidad * venta.precioVenta)} ${producto.currency}
                Ganancia: ${String.format("%.2f", ganancia)} ${producto.currency}
                ${venta.nota?.let { "Nota: $it" } ?: ""}
                """.trimIndent()
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Historial de Ventas - ${producto.name}")
            .setMessage(ventasTexto)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun venderProducto(producto: Producto) {
        android.util.Log.d("ProductosActivity", "=== INICIAR VENTA ===")
        android.util.Log.d("ProductosActivity", "Producto: ${producto.name}, Stock inicial: ${producto.quantity}")

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_vender_producto, null)
        val editTextCantidad = dialogView.findViewById<EditText>(R.id.editTextCantidadVender)
        val editTextPrecioVentaReal = dialogView.findViewById<EditText>(R.id.editTextPrecioVentaReal)
        val buttonCalculatorVentaReal = dialogView.findViewById<Button>(R.id.buttonCalculatorVentaReal)
        val editTextFechaVenta = dialogView.findViewById<EditText>(R.id.editTextFechaVenta)
        val buttonDatePickerVenta = dialogView.findViewById<Button>(R.id.buttonDatePickerVenta)
        val spinnerCuenta = dialogView.findViewById<Spinner>(R.id.spinnerCuentaDestino)
        val textViewTotalVenta = dialogView.findViewById<TextView>(R.id.textViewTotalVenta)
        val textViewProductoInfo = dialogView.findViewById<TextView>(R.id.textViewProductoInfo)
        val textViewPrecioPlaneado = dialogView.findViewById<TextView>(R.id.textViewPrecioPlaneado)
        val decimalFormat = DecimalFormat("#.##")
        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())

        // Variable para almacenar la fecha seleccionada
        var selectedDate = java.util.Date()
        editTextFechaVenta.setText(dateFormat.format(selectedDate))

        // Variable mutable para mantener el producto actualizado
        var productoActual = producto

        // Función para actualizar el total
        fun actualizarTotal() {
            val cantidad = editTextCantidad.text.toString().toDoubleOrNull() ?: 0.0
            val precioReal = editTextPrecioVentaReal.text.toString().toDoubleOrNull() ?: if (productoActual.precioVentaDefinido > 0) productoActual.precioVentaDefinido else productoActual.sellingPrice
            if (cantidad > 0) {
                val total = cantidad * precioReal
                textViewTotalVenta.text = "Total de la venta: %.2f %s".format(total, productoActual.currency)
            } else {
                textViewTotalVenta.text = "Total de la venta: 0.00 ${productoActual.currency}"
            }
        }

        // Función para actualizar la información del producto en el diálogo
        fun actualizarInfoProducto() {
            val precioReferencia = if (productoActual.precioVentaDefinido > 0) productoActual.precioVentaDefinido else productoActual.sellingPrice
            textViewProductoInfo.text = "${productoActual.name} - Stock: ${productoActual.quantity} ${productoActual.unit}"
            textViewPrecioPlaneado.text = "Precio planeado: %.2f %s".format(precioReferencia, productoActual.currency)
            // Solo actualizar el precio si el campo está vacío o es 0.0
            val precioActualStr = editTextPrecioVentaReal.text.toString()
            if (precioActualStr.isEmpty() || precioActualStr == "0.0") {
                editTextPrecioVentaReal.setText(precioReferencia.toString())
            }
            actualizarTotal()
        }

        // Mostrar información inicial del producto
        actualizarInfoProducto()

        // Configurar DatePicker
        fun showDatePicker() {
            val calendar = java.util.Calendar.getInstance()
            calendar.time = selectedDate
            val year = calendar.get(java.util.Calendar.YEAR)
            val month = calendar.get(java.util.Calendar.MONTH)
            val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)

            val datePickerDialog = android.app.DatePickerDialog(
                this@ProductosActivity,
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

        // Configurar spinner de cuentas
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val cuentasNombres = cuentas.map { "${it.name} (${it.currency})" }
            val adapterCuentas = ArrayAdapter(
                this@ProductosActivity,
                android.R.layout.simple_spinner_dropdown_item,
                cuentasNombres
            )
            spinnerCuenta.adapter = adapterCuentas
        }

        // Configurar calculadora para precio de venta real
        buttonCalculatorVentaReal.setOnClickListener {
            val calculator = CalculatorDialog(this@ProductosActivity)
            calculator.show { result ->
                editTextPrecioVentaReal.setText(decimalFormat.format(result))
            }
        }

        // Actualizar el total cuando cambia la cantidad o el precio
        editTextCantidad.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { actualizarTotal() }
        })

        editTextPrecioVentaReal.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) { actualizarTotal() }
        })
        
        AlertDialog.Builder(this)
            .setTitle("Vender ${producto.name}")
            .setView(dialogView)
            .setPositiveButton("Vender", null) // Set to null to override default behavior
            .setNegativeButton("Cancelar", null)
            .create()
            .apply {
                setOnShowListener { dialog ->
                    val button = (this as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                    button.setOnClickListener {
                        val cantidadStr = editTextCantidad.text.toString()
                        val cantidad = cantidadStr.toDoubleOrNull()

                        if (cantidad == null || cantidad <= 0) {
                            editTextCantidad.error = "Cantidad inválida"
                            return@setOnClickListener
                        }

                        if (spinnerCuenta.selectedItem == null) {
                            Toast.makeText(this@ProductosActivity, "Seleccione una cuenta destino", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }

                        // Verificar stock ANTES de procesar
                        android.util.Log.d("ProductosActivity", "Validando stock: cantidad=$cantidad, productoActual.quantity=${productoActual.quantity}")
                        if (cantidad > productoActual.quantity) {
                            android.util.Log.d("ProductosActivity", "ERROR: Stock insuficiente")
                            editTextCantidad.error = "No hay suficiente stock (disponible: ${productoActual.quantity})"
                            return@setOnClickListener
                        }

                        // Deshabilitar el botón temporalmente para evitar doble clic
                        android.util.Log.d("ProductosActivity", "Deshabilitando botón para procesar venta")
                        button.isEnabled = false

                        val precioVentaReal = editTextPrecioVentaReal.text.toString().toDoubleOrNull() ?: if (productoActual.precioVentaDefinido > 0) productoActual.precioVentaDefinido else productoActual.sellingPrice
                        android.util.Log.d("ProductosActivity", "Procesando venta: cantidad=$cantidad, precio=$precioVentaReal")

                        // Procesar la venta
                        procesarVenta(productoActual, cantidad, precioVentaReal, selectedDate, dialog, spinnerCuenta.selectedItemPosition) { productoActualizado ->
                            android.util.Log.d("ProductosActivity", "=== VENTA EXITOSA ===")
                            android.util.Log.d("ProductosActivity", "Producto actualizado: stock=${productoActualizado.quantity}")

                            // Actualizar el producto local con los datos actualizados
                            productoActual = productoActualizado
                            android.util.Log.d("ProductosActivity", "productoActual actualizado: stock=${productoActual.quantity}")

                            // Limpiar el campo de cantidad
                            editTextCantidad.text?.clear()

                            // Actualizar la información del producto en el diálogo
                            actualizarInfoProducto()

                            // Actualizar el stock en la lista principal
                            cargarProductos()

                            // Rehabilitar el botón
                            button.isEnabled = true

                            // Deshabilitar el botón si no hay stock
                            if (productoActual.quantity <= 0) {
                                button.isEnabled = false
                                Toast.makeText(this@ProductosActivity, "¡Sin stock disponible!", Toast.LENGTH_SHORT).show()
                            } else {
                                // Mostrar mensaje con el nuevo stock (solo un Toast corto)
                                Toast.makeText(this@ProductosActivity, "Stock actual: ${productoActual.quantity} ${productoActual.unit}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                // Cuando se cancela el diálogo, recargar la lista
                setOnCancelListener {
                    cargarProductos()
                }
                show()
            }
    }
    
    private fun procesarVenta(producto: Producto, cantidad: Double, precioVentaReal: Double, fechaVenta: Date, dialog: android.content.DialogInterface, cuentaIndex: Int, onVentaExitosa: (Producto) -> Unit) {
        android.util.Log.d("ProductosActivity", "=== procesarVenta INICIADO ===")
        android.util.Log.d("ProductosActivity", "Producto: ${producto.name}, Stock antes de venta: ${producto.quantity}, Cantidad a vender: $cantidad")

        lifecycleScope.launch {
            try {
                val db = AppDatabase.getInstance(applicationContext)
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }

                if (cuentaIndex < 0 || cuentaIndex >= cuentas.size) {
                    Toast.makeText(this@ProductosActivity, "Cuenta inválida", Toast.LENGTH_SHORT).show()
                    // Rehabilitar el botón en caso de error
                    val alertDialog = dialog as? AlertDialog
                    alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    return@launch
                }

                val cuentaDestino = cuentas[cuentaIndex]
                val montoTotal = cantidad * precioVentaReal
                val userId = withContext(Dispatchers.IO) {
                    cu.rge.cartera.data.DataManager.getInstance(applicationContext).getCurrentUser().email
                }

                // Registrar la transacción de ingreso con la fecha seleccionada
                val transaccion = Transaction(
                    userId = userId,
                    amount = montoTotal,
                    description = "Venta de ${cantidad} ${producto.unit} de ${producto.name} a ${precioVentaReal} ${producto.currency}/u",
                    type = "Ingreso",
                    category = "Venta Producto",
                    accountId = cuentaDestino.id,
                    date = fechaVenta
                )

                // Actualizar el stock del producto
                val productoActualizado = producto.copy(quantity = producto.quantity - cantidad)
                android.util.Log.d("ProductosActivity", "Producto actualizado en memoria: stock=${productoActualizado.quantity}")

                // Registrar la venta con el precio real y la fecha seleccionada
                val venta = VentaProducto(
                    productoId = producto.id,
                    cantidad = cantidad,
                    precioVenta = precioVentaReal,
                    moneda = producto.currency,
                    fecha = fechaVenta
                )

                try {
                    withContext(Dispatchers.IO) {
                        android.util.Log.d("ProductosActivity", "Iniciando transacción de BD")
                        db.withTransaction {
                            val transaccionId = db.transactionDao().insert(transaccion)
                            android.util.Log.d("ProductosActivity", "Transacción insertada: ID=$transaccionId")
                            val ventaConTransaccion = venta.copy(transactionId = transaccionId)
                            db.ventaProductoDao().insert(ventaConTransaccion)
                            android.util.Log.d("ProductosActivity", "Venta insertada")
                            db.productoDao().update(productoActualizado)
                            android.util.Log.d("ProductosActivity", "Producto actualizado en BD: stock=${productoActualizado.quantity}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProductosActivity", "Error en transacción: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@ProductosActivity,
                            "Error al guardar la transacción: ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                        // Rehabilitar el botón en caso de error
                        val alertDialog = dialog as? AlertDialog
                        alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                    }
                    throw e
                }

                // Recargar el producto desde la base de datos para obtener los datos actualizados
                android.util.Log.d("ProductosActivity", "Recargando producto desde BD")
                val productoRecargado = withContext(Dispatchers.IO) {
                    db.productoDao().getById(producto.id)
                }
                android.util.Log.d("ProductosActivity", "Producto recargado de BD: stock=${productoRecargado?.quantity}")

                withContext(Dispatchers.Main) {
                    android.util.Log.d("ProductosActivity", "Invocando callback con producto recargado")
                    // Notificar al callback que la venta fue exitosa y pasar el producto actualizado
                    productoRecargado?.let { onVentaExitosa(it) }
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@ProductosActivity,
                        "Error al procesar la venta: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    // Rehabilitar el botón en caso de error
                    val alertDialog = dialog as? AlertDialog
                    alertDialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
                }
            }
        }
    }
}

class ProductosAdapter(
    private val items: List<Producto>,
    val onEdit: (Producto) -> Unit,
    val onDelete: (Producto) -> Unit,
    val onVender: (Producto) -> Unit,
    val onVerDetalle: (Producto) -> Unit
) : RecyclerView.Adapter<ProductosAdapter.ViewHolder>() {
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

        // Calcular ganancia potencial y total vendido
        val gananciaPotencial = item.quantity * (item.sellingPrice - item.purchasePrice)
        holder.ganancia.text = "Gan: %.2f %s | Ven: %.2f %s".format(gananciaPotencial, item.currency, 0.0, item.currency)

        holder.itemView.setOnClickListener { onVerDetalle(item) }
        holder.deleteButton.setOnClickListener { onDelete(item) }
        holder.venderButton.setOnClickListener { onVender(item) }
        holder.editButton.setOnClickListener { onEdit(item) }

        // Deshabilitar botón vender si no hay stock
        holder.venderButton.isEnabled = item.quantity > 0
    }
    override fun getItemCount() = items.size
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nombre: android.widget.TextView = view.findViewById(R.id.textViewNombreProducto)
        val cantidad: android.widget.TextView = view.findViewById(R.id.textViewCantidadProducto)
        val precioCompra: android.widget.TextView = view.findViewById(R.id.textViewPrecioCompra)
        val ganancia: android.widget.TextView = view.findViewById(R.id.textViewGanancia)
        val deleteButton: android.widget.ImageButton = view.findViewById(R.id.buttonDeleteProducto)
        val venderButton: android.widget.Button = view.findViewById(R.id.buttonVenderProducto)
        val editButton: android.widget.Button = view.findViewById(R.id.buttonEditProducto)
    }
} 