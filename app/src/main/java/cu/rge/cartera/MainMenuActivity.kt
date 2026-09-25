package cu.rge.cartera

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import cu.rge.cartera.navigation.NavigationManager
import cu.rge.cartera.navigation.NavigationDestination

class MainMenuActivity : AppCompatActivity() {
    private lateinit var buttonAccounts: Button
    private lateinit var buttonTransactions: Button
    private lateinit var buttonReports: Button
    private lateinit var buttonSettings: Button
    private lateinit var navigationManager: NavigationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)
        
        navigationManager = NavigationManager.getInstance()

        buttonAccounts = findViewById(R.id.buttonAccounts)
        buttonTransactions = findViewById(R.id.buttonTransactions)
        buttonReports = findViewById(R.id.buttonReports)
        buttonSettings = findViewById(R.id.buttonSettings)

        buttonAccounts.setOnClickListener {
            navigationManager.navigateTo(this, NavigationDestination.ACCOUNTS)
        }

        buttonTransactions.setOnClickListener {
            navigationManager.navigateTo(this, NavigationDestination.TRANSACTIONS)
        }

        buttonReports.setOnClickListener {
            navigationManager.navigateTo(this, NavigationDestination.REPORTS)
        }

        buttonSettings.setOnClickListener {
            navigationManager.navigateTo(this, NavigationDestination.SETTINGS)
        }
    }
} 