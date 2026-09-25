package cu.rge.cartera

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.DataManager
import cu.rge.cartera.data.model.User
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RegisterActivity : AppCompatActivity() {
    private lateinit var nameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var registerButton: MaterialButton
    private lateinit var loginPrompt: TextView
    private lateinit var db: AppDatabase
    private lateinit var dataManager: DataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        db = AppDatabase.getInstance(this)
        dataManager = DataManager.getInstance(this)

        // Inicializar vistas
        nameInput = findViewById(R.id.nameInput)
        emailInput = findViewById(R.id.emailInput)
        passwordInput = findViewById(R.id.passwordInput)
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput)
        registerButton = findViewById(R.id.registerButton)
        loginPrompt = findViewById(R.id.loginPrompt)

        // Configurar listeners
        setupListeners()
    }

    private fun setupListeners() {
        registerButton.setOnClickListener {
            val name = nameInput.text.toString()
            val email = emailInput.text.toString()
            val password = passwordInput.text.toString()
            val confirmPassword = confirmPasswordInput.text.toString()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor complete todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            registerUser(email, password, name)
        }

        loginPrompt.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun registerUser(email: String, password: String, name: String) {
        lifecycleScope.launch {
            try {
                val user = User(email = email, password = password, name = name)
                withContext(Dispatchers.IO) {
                    db.userDao().insertUser(user)
                }
                Toast.makeText(this@RegisterActivity, "Usuario registrado exitosamente", Toast.LENGTH_SHORT).show()
                dataManager.setCurrentUser(email)
                startActivity(Intent(this@RegisterActivity, MainActivity::class.java))
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@RegisterActivity, "Error al registrar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}