package cu.rge.cartera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.TarifaPago
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TarifaPagoActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TarifaPagoAdapter
    private lateinit var fabAdd: FloatingActionButton
    private val tarifas = mutableListOf<TarifaPago>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tarifa_pago)

        recyclerView = findViewById(R.id.recyclerViewTarifas)
        fabAdd = findViewById(R.id.fabAddTarifa)
        adapter = TarifaPagoAdapter(tarifas,
            onEdit = { tarifa -> mostrarDialogoTarifa(tarifa) },
            onDelete = { tarifa -> eliminarTarifa(tarifa) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener { mostrarDialogoTarifa(null) }
        cargarTarifas()
    }

    private fun cargarTarifas() {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val lista = withContext(Dispatchers.IO) { db.tarifaPagoDao().getAll() }
            tarifas.clear()
            tarifas.addAll(lista)
            adapter.notifyDataSetChanged()
        }
    }

    private fun mostrarDialogoTarifa(tarifa: TarifaPago?) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tarifa_pago, null)
        val editNombre = dialogView.findViewById<EditText>(R.id.editTextNombreTarifa)
        val editPorcentaje = dialogView.findViewById<EditText>(R.id.editTextPorcentajeTarifa)
        val editDescripcion = dialogView.findViewById<EditText>(R.id.editTextDescripcionTarifa)
        if (tarifa != null) {
            editNombre.setText(tarifa.nombre)
            editPorcentaje.setText(tarifa.porcentaje.toString())
            editDescripcion.setText(tarifa.descripcion)
        }
        AlertDialog.Builder(this)
            .setTitle(if (tarifa == null) "Nueva tarifa" else "Editar tarifa")
            .setView(dialogView)
            .setPositiveButton("Guardar") { dialog, _ ->
                val nombre = editNombre.text.toString().trim()
                val porcentaje = editPorcentaje.text.toString().toDoubleOrNull() ?: 0.0
                val descripcion = editDescripcion.text.toString().trim()
                if (nombre.isEmpty() || porcentaje <= 0.0) {
                    Toast.makeText(this, "Datos inválidos", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    if (tarifa == null) {
                        withContext(Dispatchers.IO) {
                            db.tarifaPagoDao().insert(TarifaPago(nombre = nombre, porcentaje = porcentaje, descripcion = descripcion))
                        }
                    } else {
                        withContext(Dispatchers.IO) {
                            db.tarifaPagoDao().update(tarifa.copy(nombre = nombre, porcentaje = porcentaje, descripcion = descripcion))
                        }
                    }
                    cargarTarifas()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun eliminarTarifa(tarifa: TarifaPago) {
        AlertDialog.Builder(this)
            .setTitle("Eliminar tarifa")
            .setMessage("¿Seguro que deseas eliminar esta tarifa?")
            .setPositiveButton("Eliminar") { _, _ ->
                lifecycleScope.launch {
                    val db = AppDatabase.getInstance(applicationContext)
                    withContext(Dispatchers.IO) { db.tarifaPagoDao().delete(tarifa) }
                    cargarTarifas()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
} 