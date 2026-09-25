package cu.rge.cartera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Spinner
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.ImageButton
import java.text.DecimalFormat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.DataManager
import cu.rge.cartera.data.model.PersonaRelacionada
import cu.rge.cartera.data.model.AbonoPersona
import cu.rge.cartera.data.model.Transaction
import cu.rge.cartera.navigation.DataUpdateObserver
import cu.rge.cartera.navigation.DataUpdateType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import android.widget.TextView
import android.widget.Button
import android.widget.LinearLayout
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

class PersonasRelacionadasActivity : AppCompatActivity() {
    private lateinit var adapter: PersonasAdapter
    private val personas = mutableListOf<PersonaRelacionada>()
    private var tipo: String = "DEUDOR"
    private lateinit var dataUpdateObserver: DataUpdateObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_personas_relacionadas)

        tipo = intent.getStringExtra("tipo") ?: "DEUDOR"
        dataUpdateObserver = DataUpdateObserver.getInstance()
        val titulo = if (tipo == "DEUDOR") "Deudores" else "Acreedores"
        findViewById<android.widget.TextView>(R.id.textViewTitulo).text = titulo

        val recyclerView = findViewById<RecyclerView>(R.id.recyclerViewPersonas)
        adapter = PersonasAdapter(personas,
            onEdit = { persona -> mostrarDetallePersona(persona) },
            onDelete = { persona -> eliminarPersona(persona) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        findViewById<FloatingActionButton>(R.id.fabAgregarPersona).setOnClickListener {
            mostrarDialogoPersona(null)
        }

        cargarPersonas()
    }

    private fun cargarPersonas() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val lista = withContext(Dispatchers.IO) {
                db.personaRelacionadaDao().getByTipoAndActiva(tipo)
            }
            personas.clear()
            personas.addAll(lista)
            adapter.notifyDataSetChanged()
            
            if (lista.isEmpty()) {
                Toast.makeText(
                    this@PersonasRelacionadasActivity,
                    if (tipo == "DEUDOR") "¡No hay deudas pendientes!" else "¡No hay acreencias pendientes!",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun mostrarDialogoPersona(persona: PersonaRelacionada?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_persona_relacionada, null)
        val editTextNombre = dialogView.findViewById<EditText>(R.id.editTextNombre)
        val editTextMonto = dialogView.findViewById<EditText>(R.id.editTextMonto)
        val spinnerMoneda = dialogView.findViewById<Spinner>(R.id.spinnerMoneda)
        val spinnerCuenta = dialogView.findViewById<Spinner>(R.id.spinnerCuenta)
        val editTextDescripcion = dialogView.findViewById<EditText>(R.id.editTextDescripcion)
        val buttonCalculator = dialogView.findViewById<Button>(R.id.buttonCalculator)
        val decimalFormat = DecimalFormat("#.##")

        val monedas = listOf("CUP", "MLC", "USD")
        spinnerMoneda.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, monedas)

        // Cargar cuentas en el spinner
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
            val listaCuentas = listOf("Sin cuenta") + cuentas.map { "${it.name} (${it.currency})" }
            val adapterCuentas = ArrayAdapter(this@PersonasRelacionadasActivity, android.R.layout.simple_spinner_item, listaCuentas)
            adapterCuentas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCuenta.adapter = adapterCuentas
            
            if (persona != null && persona.accountId != null) {
                val cuentaIndex = cuentas.indexOfFirst { it.id == persona.accountId }
                if (cuentaIndex >= 0) {
                    spinnerCuenta.setSelection(cuentaIndex + 1) // +1 porque el primer elemento es "Sin cuenta"
                }
            }
        }

        if (persona != null) {
            editTextNombre.setText(persona.nombre)
            editTextMonto.setText(persona.monto.toString())
            spinnerMoneda.setSelection(monedas.indexOf(persona.moneda))
            editTextDescripcion.setText(persona.descripcion ?: "")
        }

        // Configurar botón de calculadora
        buttonCalculator.setOnClickListener {
            val calculator = CalculatorDialog(this@PersonasRelacionadasActivity)
            calculator.show { result ->
                editTextMonto.setText(decimalFormat.format(result))
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(if (persona == null) "Agregar ${if (tipo == "DEUDOR") "Deudor" else "Acreedor"}" else "Editar ${persona.nombre}")
            .setView(dialogView)
            .setPositiveButton("Guardar") { _, _ ->
                val nombre = editTextNombre.text.toString()
                val monto = editTextMonto.text.toString().toDoubleOrNull() ?: 0.0
                val moneda = spinnerMoneda.selectedItem.toString()
                val descripcion = editTextDescripcion.text.toString()
                val posCuenta = spinnerCuenta.selectedItemPosition

                guardarPersona(persona, nombre, monto, moneda, descripcion, posCuenta)
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()
    }

    private fun guardarPersona(persona: PersonaRelacionada?, nombre: String, monto: Double, moneda: String, descripcion: String, posCuenta: Int) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            
            val accountId: Long? = if (posCuenta > 0) {
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                cuentas[posCuenta - 1].id
            } else null

            val personaActualizada = persona?.copy(
                nombre = nombre,
                monto = monto,
                moneda = moneda,
                descripcion = if (descripcion.isBlank()) null else descripcion,
                accountId = accountId
            ) ?: PersonaRelacionada(
                nombre = nombre,
                monto = monto,
                tipo = tipo,
                moneda = moneda,
                descripcion = if (descripcion.isBlank()) null else descripcion,
                accountId = accountId
            )

            withContext(Dispatchers.IO) {
                if (personaActualizada.id == 0L) {
                    // Es una nueva persona, crear transacción si tiene cuenta
                    if (accountId != null) {
                        val cuenta = db.accountDao().getById(accountId)
                        if (cuenta != null) {
                            val userId = DataManager.getInstance(applicationContext).getCurrentUser().email
                            val descripcionTrans = if (tipo == "DEUDOR") {
                                "Préstamo a $nombre" + (if (descripcion.isNotBlank()) ": $descripcion" else "")
                            } else {
                                "Préstamo de $nombre" + (if (descripcion.isNotBlank()) ": $descripcion" else "")
                            }
                            val tipoTrans = if (tipo == "DEUDOR") "Gasto" else "Ingreso"
                            android.util.Log.d("PersonasRelacionadasActivity", "Creando transacción:")
                            android.util.Log.d("PersonasRelacionadasActivity", "  Moneda persona: $moneda")
                            android.util.Log.d("PersonasRelacionadasActivity", "  Moneda cuenta: ${cuenta.currency}")
                            android.util.Log.d("PersonasRelacionadasActivity", "  Monto original: $monto")
                            android.util.Log.d("PersonasRelacionadasActivity", "  Tipo: $tipo")
                            val montoTrans = if (moneda == cuenta.currency) {
                                android.util.Log.d("PersonasRelacionadasActivity", "  Mismas monedas, sin conversión")
                                if (tipo == "DEUDOR") -monto else monto
                            } else {
                                val rate = db.currencyRateDao().getSmartRate(moneda, cuenta.currency) ?: 1.0
                                android.util.Log.d("PersonasRelacionadasActivity", "  Rate ($moneda -> ${cuenta.currency}): $rate")
                                val montoConvertido = monto * rate
                                android.util.Log.d("PersonasRelacionadasActivity", "  Monto convertido: $montoConvertido")
                                if (tipo == "DEUDOR") -montoConvertido else montoConvertido
                            }
                            android.util.Log.d("PersonasRelacionadasActivity", "  Monto transacción final: $montoTrans")
                            val trans = Transaction(
                                userId = userId,
                                amount = montoTrans,
                                description = descripcionTrans,
                                type = tipoTrans,
                                category = "Préstamo $tipo",
                                accountId = accountId,
                                date = Date()
                            )
                            db.transactionDao().insert(trans)
                        }
                    }
                    db.personaRelacionadaDao().insert(personaActualizada)
                } else {
                    // Es una edición, no crear transacción nueva
                    db.personaRelacionadaDao().update(personaActualizada)
                }
            }
            cargarPersonas()
            dataUpdateObserver.notifyDataChanged(DataUpdateType.PERSONAS)
            if (accountId != null && personaActualizada.id == 0L) {
                dataUpdateObserver.notifyDataChanged(DataUpdateType.TRANSACTIONS)
            }
        }
    }

    private fun eliminarPersona(persona: PersonaRelacionada) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar")
            .setMessage("¿Estás seguro de que deseas eliminar a ${persona.nombre}?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) {
                        db.personaRelacionadaDao().delete(persona)
                    }
                    cargarPersonas()
                    dataUpdateObserver.notifyDataChanged(DataUpdateType.PERSONAS)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDetallePersona(persona: PersonaRelacionada) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_detalle_persona, null)

        var detalleDialog: AlertDialog? = null

        val textViewNombre = dialogView.findViewById<TextView>(R.id.textViewNombrePersona)
        val textViewDescripcion = dialogView.findViewById<TextView>(R.id.textViewDescripcionPersona)
        val textViewMontoOriginal = dialogView.findViewById<TextView>(R.id.textViewMontoOriginal)
        val textViewMontoPendiente = dialogView.findViewById<TextView>(R.id.textViewMontoPendiente)
        val textViewEstado = dialogView.findViewById<TextView>(R.id.textViewEstadoDeuda)
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
        textViewDescripcion.visibility = if (persona.descripcion.isNullOrBlank()) View.GONE else View.VISIBLE
        textViewMontoOriginal.text = "Monto original: %.2f %s".format(persona.monto, persona.moneda)

        val abonosList = mutableListOf<AbonoPersona>()
        lateinit var abonosAdapter: RecyclerView.Adapter<AbonoViewHolder>

        fun actualizarPendienteYHistorial() {
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val abonos = withContext(Dispatchers.IO) { db.abonoPersonaDao().getByPersona(persona.id) }
                abonosList.clear()
                abonosList.addAll(abonos)
                abonosAdapter.notifyDataSetChanged()
                var pendiente = persona.monto
                for (ab in abonos) {
                    val rate = if (ab.moneda == persona.moneda) 1.0 else withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(ab.moneda, persona.moneda) ?: 1.0
                    }
                    pendiente += ab.monto * rate
                }
                textViewMontoPendiente.text = "Monto pendiente: %.2f %s".format(pendiente, persona.moneda)
                textViewEstado.text = if (pendiente <= 0.01) "Pagada" else ""
            }
        }

        abonosAdapter = object : RecyclerView.Adapter<AbonoViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbonoViewHolder {
                val view = LayoutInflater.from(parent.context).inflate(R.layout.item_abono, parent, false)
                return AbonoViewHolder(view)
            }

            override fun onBindViewHolder(holder: AbonoViewHolder, position: Int) {
                val abono = abonosList[position]
                holder.monto.text = "${"%.2f".format(abono.monto)} ${abono.moneda}"
                holder.fecha.text = DateFormat.getDateInstance(DateFormat.MEDIUM).format(abono.fecha)
                holder.comentario.text = abono.nota ?: "Sin nota"
                holder.deleteButton.setOnClickListener {
                    AlertDialog.Builder(this@PersonasRelacionadasActivity)
                        .setTitle("Eliminar abono")
                        .setMessage("¿Deseas eliminar este abono?")
                        .setPositiveButton("Eliminar") { _, _ ->
                            eliminarAbonoPersona(abono) {
                                val index = holder.adapterPosition
                                if (index != RecyclerView.NO_POSITION) {
                                    abonosList.removeAt(index)
                                    notifyItemRemoved(index)
                                    actualizarPendienteYHistorial()
                                }
                            }
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                }
            }

            override fun getItemCount(): Int = abonosList.size
        }

        recyclerViewAbonos.layoutManager = LinearLayoutManager(this)
        recyclerViewAbonos.adapter = abonosAdapter

        actualizarPendienteYHistorial()

        suspend fun manejarCambioDeEstado(db: AppDatabase): Boolean {
            val abonos = withContext(Dispatchers.IO) { db.abonoPersonaDao().getByPersona(persona.id) }
            var pendiente = persona.monto
            for (ab in abonos) {
                val rate = if (ab.moneda == persona.moneda) 1.0 else withContext(Dispatchers.IO) {
                    db.currencyRateDao().getSmartRate(ab.moneda, persona.moneda) ?: 1.0
                }
                pendiente += ab.monto * rate
            }
            val debeCambiarAcreedor = persona.tipo == "DEUDOR" && pendiente < -0.01
            val debeCambiarDeudor = persona.tipo == "ACREEDOR" && pendiente > 0.01
            if (debeCambiarAcreedor || debeCambiarDeudor) {
                val nuevoTipo = if (persona.tipo == "DEUDOR") "ACREEDOR" else "DEUDOR"

                // Verificar si ya existe una persona con el mismo nombre en el otro tipo
                val personaExistente = withContext(Dispatchers.IO) {
                    db.personaRelacionadaDao().getByNombreAndTipo(persona.nombre, nuevoTipo)
                }

                if (personaExistente != null) {
                    // Ya existe una persona con el mismo nombre en el otro tipo
                    // Transferir los abonos a esa persona y marcar la actual como inactiva
                    withContext(Dispatchers.IO) {
                        // Transferir abonos
                        for (abono in abonos) {
                            db.abonoPersonaDao().update(
                                abono.copy(personaId = personaExistente.id)
                            )
                        }

                        // Actualizar el monto de la persona existente
                        val nuevoMonto = personaExistente.monto + kotlin.math.abs(pendiente)
                        db.personaRelacionadaDao().update(
                            personaExistente.copy(
                                monto = nuevoMonto,
                                fecha = Date()
                            )
                        )

                        // Marcar la persona actual como inactiva (no eliminar para mantener historial)
                        db.personaRelacionadaDao().update(
                            persona.copy(
                                activa = false,
                                fecha = Date()
                            )
                        )
                    }

                    val saldoTexto = "%.2f".format(kotlin.math.abs(pendiente))
                    val mensajeCambio = if (nuevoTipo == "ACREEDOR") {
                        "${persona.nombre} se ha unido al registro existente de ACREEDORES con un saldo adicional de $saldoTexto ${persona.moneda}"
                    } else {
                        "${persona.nombre} se ha unido al registro existente de DEUDORES con un saldo adicional de $saldoTexto ${persona.moneda}"
                    }
                    AlertDialog.Builder(this@PersonasRelacionadasActivity)
                        .setTitle("Cambio de estado")
                        .setMessage(mensajeCambio)
                        .setPositiveButton("Aceptar", null)
                        .show()
                    detalleDialog?.dismiss()
                    return true
                } else {
                    // No existe, cambiar el tipo como normalmente
                    withContext(Dispatchers.IO) {
                        db.personaRelacionadaDao().update(
                            persona.copy(
                                tipo = nuevoTipo,
                                fecha = Date()
                            )
                        )
                    }
                    val saldoTexto = "%.2f".format(kotlin.math.abs(pendiente))
                    val mensajeCambio = if (nuevoTipo == "ACREEDOR") {
                        "${persona.nombre} ahora es un ACREEDOR con un saldo de $saldoTexto ${persona.moneda}"
                    } else {
                        "${persona.nombre} ahora es un DEUDOR con un saldo de $saldoTexto ${persona.moneda}"
                    }
                    AlertDialog.Builder(this@PersonasRelacionadasActivity)
                        .setTitle("Cambio de estado")
                        .setMessage(mensajeCambio)
                        .setPositiveButton("Aceptar", null)
                        .show()
                    detalleDialog?.dismiss()
                    return true
                }
            }
            return false
        }

        buttonExpandirAbono.setOnClickListener {
            if (layoutFormularioAbono.visibility == View.GONE) {
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                    if (cuentas.isEmpty()) {
                        Toast.makeText(this@PersonasRelacionadasActivity, "No hay cuentas disponibles. Crea una cuenta primero.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    val listaCuentas = listOf("Sin cuenta") + cuentas.map { "${it.name} (${it.currency})" }
                    val adapterCuentas = ArrayAdapter(this@PersonasRelacionadasActivity, android.R.layout.simple_spinner_item, listaCuentas)
                    adapterCuentas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCuentaAbono.adapter = adapterCuentas
                    layoutFormularioAbono.visibility = View.VISIBLE
                    buttonExpandirAbono.text = "Cancelar"
                }
            } else {
                layoutFormularioAbono.visibility = View.GONE
                buttonExpandirAbono.text = "Registrar abono"
                editTextMontoAbono.text.clear()
                editTextNotaAbono.text.clear()
            }
        }

        buttonConfirmarAbono.setOnClickListener {
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
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) {
                        db.abonoPersonaDao().insert(
                            AbonoPersona(
                                personaId = persona.id,
                                monto = monto,
                                moneda = persona.moneda,
                                nota = nota,
                                fecha = Date(),
                                transactionId = null
                            )
                        )
                    }
                    editTextMontoAbono.text.clear()
                    editTextNotaAbono.text.clear()
                    layoutFormularioAbono.visibility = View.GONE
                    buttonExpandirAbono.text = "Registrar abono"
                    actualizarPendienteYHistorial()
                    val huboCambio = manejarCambioDeEstado(db)
                    dataUpdateObserver.notifyDataChanged(DataUpdateType.PERSONAS)
                    if (huboCambio) {
                        cargarPersonas()
                    }
                    Toast.makeText(this@PersonasRelacionadasActivity, "Abono registrado (sin cuenta)", Toast.LENGTH_SHORT).show()
                }
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                val cuentaSeleccionada = cuentas[posCuenta - 1]
                val userId = withContext(Dispatchers.IO) { DataManager.getInstance(applicationContext).getCurrentUser().email }
                val descripcionTrans = if (persona.tipo == "DEUDOR") {
                    "Nuevo préstamo a ${persona.nombre}" + (if (!nota.isNullOrBlank()) ": $nota" else "")
                } else {
                    "Nuevo préstamo de ${persona.nombre}" + (if (!nota.isNullOrBlank()) ": $nota" else "")
                }
                val tipoTrans = if (persona.tipo == "DEUDOR") "Gasto" else "Ingreso"
                val montoTrans = if (persona.moneda == cuentaSeleccionada.currency) {
                    if (persona.tipo == "DEUDOR") -abs(monto) else abs(monto)
                } else {
                    val rate = withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(persona.moneda, cuentaSeleccionada.currency) ?: 1.0
                    }
                    val montoConvertido = monto * rate
                    if (persona.tipo == "DEUDOR") -abs(montoConvertido) else abs(montoConvertido)
                }
                val trans = Transaction(
                    userId = userId,
                    amount = montoTrans,
                    description = descripcionTrans,
                    type = tipoTrans,
                    category = "Abono ${persona.tipo}",
                    accountId = cuentaSeleccionada.id,
                    date = Date()
                )
                val transactionId = withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                withContext(Dispatchers.IO) {
                    db.abonoPersonaDao().insert(
                        AbonoPersona(
                            personaId = persona.id,
                            monto = monto,
                            moneda = persona.moneda,
                            nota = nota,
                            fecha = Date(),
                            transactionId = transactionId
                        )
                    )
                }
                editTextMontoAbono.text.clear()
                editTextNotaAbono.text.clear()
                layoutFormularioAbono.visibility = View.GONE
                buttonExpandirAbono.text = "Registrar abono"
                actualizarPendienteYHistorial()
                val huboCambio = manejarCambioDeEstado(db)
                dataUpdateObserver.notifyDataChanged(DataUpdateType.PERSONAS)
                if (huboCambio) {
                    cargarPersonas()
                }
                dataUpdateObserver.notifyDataChanged(DataUpdateType.TRANSACTIONS)
                Toast.makeText(this@PersonasRelacionadasActivity, "Abono registrado y transacción creada", Toast.LENGTH_SHORT).show()
            }
        }

        buttonLiquidarDeuda.setOnClickListener {
            val dialogLiquidar = layoutInflater.inflate(R.layout.dialog_liquidar_deuda, null)
            val spinnerCuenta = dialogLiquidar.findViewById<Spinner>(R.id.spinnerCuenta)
            val textViewSaldoCuenta = dialogLiquidar.findViewById<TextView>(R.id.textViewSaldoCuenta)
            val editTextMonto = dialogLiquidar.findViewById<EditText>(R.id.editTextMonto)
            lifecycleScope.launch {
                val db = AppDatabase.getInstance(applicationContext)
                val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                val listaCuentas = listOf("Sin cuenta") + cuentas.map { "${it.name} (${it.currency})" }
                val adapterCuentas = ArrayAdapter(this@PersonasRelacionadasActivity, android.R.layout.simple_spinner_item, listaCuentas)
                adapterCuentas.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerCuenta.adapter = adapterCuentas
                spinnerCuenta.setSelection(0)
                spinnerCuenta.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
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

                    override fun onNothingSelected(parent: AdapterView<*>) {}
                }
            }
            val alertDialog = AlertDialog.Builder(this)
                .setTitle(if (persona.tipo == "DEUDOR") "Aumentar deuda" else "Liquidar acreencia")
                .setView(dialogLiquidar)
                .setPositiveButton("Liquidar", null)
                .setNegativeButton("Cancelar", null)
                .create()
            alertDialog.setOnShowListener {
                alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val monto = editTextMonto.text.toString().toDoubleOrNull() ?: textViewMontoPendiente.text.toString().substringAfter(": ").substringBefore(" ").toDoubleOrNull() ?: 0.0
                    val posCuenta = spinnerCuenta.selectedItemPosition
                    val nota = "Liquidación total"
                    if (monto <= 0.0) {
                        Toast.makeText(this, "Monto inválido", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (posCuenta < 0) {
                        Toast.makeText(this, "Selecciona una cuenta", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (posCuenta == 0) {
                        lifecycleScope.launch {
                            val db = AppDatabase.getInstance(applicationContext)
                            withContext(Dispatchers.IO) {
                                db.abonoPersonaDao().insert(
                                    AbonoPersona(
                                        personaId = persona.id,
                                        monto = -abs(monto),
                                        moneda = persona.moneda,
                                        nota = nota,
                                        fecha = Date(),
                                        transactionId = null
                                    )
                                )
                            }
                            actualizarPendienteYHistorial()
                            val huboCambio = manejarCambioDeEstado(db)
                            dataUpdateObserver.notifyPersonasChanged()
                            dataUpdateObserver.notifyDataChanged(DataUpdateType.TRANSACTIONS)
                            cargarPersonas()
                            Toast.makeText(this@PersonasRelacionadasActivity, "Deuda liquidada (sin cuenta)", Toast.LENGTH_SHORT).show()
                            alertDialog.dismiss()
                            if (huboCambio) {
                                detalleDialog?.dismiss()
                            }
                        }
                        return@setOnClickListener
                    }
                    lifecycleScope.launch {
                        val db = AppDatabase.getInstance(applicationContext)
                        val cuentas = withContext(Dispatchers.IO) { db.accountDao().getAll() }
                        val cuentaSeleccionada = cuentas[posCuenta - 1]
                        val userId = withContext(Dispatchers.IO) { DataManager.getInstance(applicationContext).getCurrentUser().email }
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
                                Toast.makeText(this@PersonasRelacionadasActivity, "Saldo insuficiente en la cuenta seleccionada", Toast.LENGTH_LONG).show()
                                return@launch
                            }
                        }
                        val descripcionTrans = if (persona.tipo == "DEUDOR") {
                            "Pago recibido de ${persona.nombre} (liquidación)"
                        } else {
                            "Pago realizado a ${persona.nombre} (liquidación)"
                        }
                        val tipoTrans = if (persona.tipo == "DEUDOR") "Ingreso" else "Gasto"
                        val montoTrans = if (persona.moneda == cuentaSeleccionada.currency) {
                            if (persona.tipo == "DEUDOR") abs(monto) else -abs(monto)
                        } else {
                            val rate = withContext(Dispatchers.IO) {
                                db.currencyRateDao().getSmartRate(persona.moneda, cuentaSeleccionada.currency) ?: 1.0
                            }
                            val montoConvertido = monto * rate
                            if (persona.tipo == "DEUDOR") abs(montoConvertido) else -abs(montoConvertido)
                        }
                        val trans = Transaction(
                            userId = userId,
                            amount = montoTrans,
                            description = descripcionTrans,
                            type = tipoTrans,
                            category = "Abono ${persona.tipo}",
                            accountId = cuentaSeleccionada.id,
                            date = Date()
                        )
                        val transactionId = withContext(Dispatchers.IO) { db.transactionDao().insert(trans) }
                        withContext(Dispatchers.IO) {
                            db.abonoPersonaDao().insert(
                                AbonoPersona(
                                    personaId = persona.id,
                                    monto = -abs(monto),
                                    moneda = persona.moneda,
                                    nota = nota,
                                    fecha = Date(),
                                    transactionId = transactionId
                                )
                            )
                        }
                        actualizarPendienteYHistorial()
                        val huboCambio = manejarCambioDeEstado(db)
                        cargarPersonas()
                        dataUpdateObserver.notifyPersonasChanged()
                        dataUpdateObserver.notifyDataChanged(DataUpdateType.TRANSACTIONS)
                        Toast.makeText(this@PersonasRelacionadasActivity, "Deuda liquidada y transacción creada", Toast.LENGTH_SHORT).show()
                        alertDialog.dismiss()
                        if (huboCambio) {
                            detalleDialog?.dismiss()
                        }
                    }
                }
            }
            alertDialog.show()
        }

        detalleDialog = AlertDialog.Builder(this)
            .setTitle("Detalle de ${persona.nombre}")
            .setView(dialogView)
            .setNegativeButton("Cerrar", null)
            .create()
        detalleDialog?.show()
    }

    private fun eliminarAbonoPersona(abono: AbonoPersona, onDeleted: (() -> Unit)? = null) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            withContext(Dispatchers.IO) {
                abono.transactionId?.let { db.transactionDao().deleteById(it) }
                db.abonoPersonaDao().delete(abono)
            }
            onDeleted?.invoke()
            Toast.makeText(this@PersonasRelacionadasActivity, "Abono eliminado", Toast.LENGTH_SHORT).show()
            dataUpdateObserver.notifyDataChanged(DataUpdateType.PERSONAS)
        }
    }

    class AbonoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val monto: TextView = view.findViewById(R.id.textViewAbonoMonto)
        val fecha: TextView = view.findViewById(R.id.textViewAbonoFecha)
        val comentario: TextView = view.findViewById(R.id.textViewAbonoNota)
        val deleteButton: ImageButton = view.findViewById(R.id.buttonDeleteAbono)
    }

    class PersonasAdapter(
        private val items: List<PersonaRelacionada>,
        val onEdit: (PersonaRelacionada) -> Unit,
        val onDelete: (PersonaRelacionada) -> Unit
    ) : RecyclerView.Adapter<PersonasAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_persona_relacionada, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.nombre.text = item.nombre
            holder.descripcion.text = item.descripcion ?: ""
            holder.monto.text = "%.2f %s".format(item.monto, item.moneda)
            holder.itemView.setOnClickListener { onEdit(item) }
            holder.deleteButton.setOnClickListener { onDelete(item) }
            
            // Mostrar 'Pagada' si corresponde
            val context = holder.itemView.context
            val db = AppDatabase.getInstance(context)
            (context as? AppCompatActivity)?.lifecycleScope?.launch {
                val abonos = withContext(Dispatchers.IO) { db.abonoPersonaDao().getByPersona(item.id) }
                var pendiente = item.monto
                for (ab in abonos) {
                    val rate = if (ab.moneda == item.moneda) 1.0 else withContext(Dispatchers.IO) {
                        db.currencyRateDao().getSmartRate(ab.moneda, item.moneda) ?: 1.0
                    }
                    pendiente += ab.monto * rate
                }
                if (pendiente <= 0.01) {
                    holder.nombre.text = "${item.nombre} (Pagada)"
                }
            }
        }

        override fun getItemCount() = items.size
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val nombre: TextView = view.findViewById(R.id.textViewPersonaNombre)
            val descripcion: TextView = view.findViewById(R.id.textViewPersonaDescripcion)
            val monto: TextView = view.findViewById(R.id.textViewPersonaMonto)
            val deleteButton: ImageButton = view.findViewById(R.id.buttonDeletePersona)
        }
    }
}
