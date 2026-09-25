package cu.rge.cartera

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cu.rge.cartera.data.DataManager
import cu.rge.cartera.databinding.ActivityProfileBinding
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private lateinit var dataManager: DataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        dataManager = DataManager.getInstance(applicationContext)

        setupToolbar()
        loadUserData()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                val user = dataManager.getCurrentUser()
                binding.userName.text = user.name
                binding.userEmail.text = user.email

                val totalIncome = dataManager.getTotalByType("Ingreso")
                val totalExpenses = dataManager.getTotalByType("Gasto")

                binding.totalIncome.text = String.format("$%.2f", totalIncome)
                binding.totalExpenses.text = String.format("$%.2f", totalExpenses)
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupButtons() {
        binding.editProfileButton.setOnClickListener {
            Toast.makeText(this, "Funcionalidad en desarrollo", Toast.LENGTH_SHORT).show()
        }
    }
} 