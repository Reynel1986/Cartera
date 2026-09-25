package cu.rge.cartera

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cu.rge.cartera.data.AppDatabase
import cu.rge.cartera.data.DataManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            verificarAutenticacion()
        }, 2000)
    }

    private fun verificarAutenticacion() {
        lifecycleScope.launch {
            val dataManager = DataManager.getInstance(applicationContext)
            val isLogged = withContext(Dispatchers.IO) {
                try {
                    dataManager.getCurrentUser()
                    true
                } catch (e: Exception) {
                    false
                }
            }

            if (isLogged) {
                val intent = Intent(this@SplashActivity, MainActivity::class.java)
                startActivity(intent)
            } else {
                val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                startActivity(intent)
            }
            finish()
        }
    }
} 