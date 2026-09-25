package cu.rge.cartera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Clase auxiliar para categoría con nivel
data class CategoryWithLevel(val category: Category, val level: Int)

class CategoriesActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CategoryAdapter
    private val categories = mutableListOf<CategoryWithLevel>()
    private lateinit var db: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_categories)

        val toolbar: com.google.android.material.appbar.MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerViewCategories)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoryAdapter(categories) { category ->
            showDeleteCategoryDialog(category)
        }
        recyclerView.adapter = adapter

        db = AppDatabase.getInstance(applicationContext)
        loadCategories()
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { db.categoryDao().getAll() }
            categories.clear()
            categories.addAll(orderCategoriesHierarchically(list))
            adapter.notifyDataSetChanged()
        }
    }

    // Función para ordenar las categorías jerárquicamente y guardar el nivel
    private fun orderCategoriesHierarchically(list: List<Category>): List<CategoryWithLevel> {
        val map = list.groupBy { it.parentId }
        val result = mutableListOf<CategoryWithLevel>()
        val principales = map[null]?.sortedBy { it.id } ?: emptyList()
        fun addChildren(parentId: Long?, level: Int) {
            val hijos = map[parentId]?.sortedBy { it.id } ?: emptyList()
            for (cat in hijos) {
                result.add(CategoryWithLevel(cat, level))
                addChildren(cat.id, level + 1)
            }
        }
        for (cat in principales) {
            result.add(CategoryWithLevel(cat, 0))
            addChildren(cat.id, 1)
        }
        val idsIncluidos = result.map { it.category.id }.toSet()
        val huerfanas = list.filter { it.parentId == null && it.id !in idsIncluidos }
        result.addAll(huerfanas.map { CategoryWithLevel(it, 0) })
        return result
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.categories_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_category -> {
                showAddCategoryDialog()
                true
            }
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showAddCategoryDialog() {
        lifecycleScope.launch {
            // Obtener categorías existentes para el Spinner
            val categoryList = withContext(Dispatchers.IO) { db.categoryDao().getAll() }
            val spinnerItems = mutableListOf("Ninguna")
            spinnerItems.addAll(categoryList.map { it.name })

            val dialogView = LayoutInflater.from(this@CategoriesActivity).inflate(R.layout.dialog_add_category, null)
            val spinner = dialogView.findViewById<android.widget.Spinner>(R.id.spinnerParentCategory)
            val adapterSpinner = android.widget.ArrayAdapter(
                this@CategoriesActivity,
                android.R.layout.simple_spinner_item,
                spinnerItems
            )
            adapterSpinner.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapterSpinner

            val dialog = AlertDialog.Builder(this@CategoriesActivity)
                .setView(dialogView)
                .create()

            val buttonAdd = dialogView.findViewById<android.widget.Button>(R.id.buttonAddCategory)
            buttonAdd.setOnClickListener {
                val name = dialogView.findViewById<android.widget.EditText>(R.id.editTextCategoryName).text.toString()
                val limit = dialogView.findViewById<android.widget.EditText>(R.id.editTextMonthlyLimit).text.toString()
                val parentPosition = spinner.selectedItemPosition
                val parentId = if (parentPosition == 0) null else categoryList[parentPosition - 1].id

                if (name.isBlank()) {
                    Toast.makeText(this@CategoriesActivity, "El nombre es obligatorio", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                lifecycleScope.launch {
                    val category = Category(
                        name = name,
                        parentId = parentId,
                        monthlyLimit = limit.toDoubleOrNull()
                    )
                    withContext(Dispatchers.IO) { db.categoryDao().insert(category) }
                    loadCategories()
                }
                dialog.dismiss()
            }
            dialog.show()
        }
    }

    private fun showDeleteCategoryDialog(category: Category) {
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val transactionCount = withContext(Dispatchers.IO) { db.transactionDao().countByCategory(category.name) }
            if (transactionCount > 0) {
                AlertDialog.Builder(this@CategoriesActivity)
                    .setTitle("No se puede borrar")
                    .setMessage("Esta categoría tiene transacciones asociadas y no puede ser eliminada.")
                    .setPositiveButton("Aceptar", null)
                    .show()
                return@launch
            }
            AlertDialog.Builder(this@CategoriesActivity)
                .setTitle("Eliminar categoría")
                .setMessage("¿Seguro que deseas eliminar la categoría '${category.name}'?")
                .setPositiveButton("Eliminar") { _, _ ->
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { db.categoryDao().delete(category) }
                        loadCategories()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}

// Adaptador para la lista de categorías
class CategoryAdapter(private val items: List<CategoryWithLevel>, private val onDelete: (Category) -> Unit) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.name.text = item.category.name
        holder.name.setPadding(48 * item.level, holder.name.paddingTop, holder.name.paddingRight, holder.name.paddingBottom)
        holder.icon.setImageResource(android.R.drawable.ic_menu_gallery)
        holder.buttonDelete.setOnClickListener {
            onDelete(item.category)
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imageViewIcon)
        val name: TextView = view.findViewById(R.id.textViewCategoryName)
        val buttonDelete: android.widget.ImageButton = view.findViewById(R.id.buttonDeleteCategory)
    }
} 