package cu.rge.cartera.navigation

import android.content.Context
import android.content.Intent
import cu.rge.cartera.*

/**
 * Gestor centralizado de navegación que unifica la lógica de navegación
 * entre MainActivity y MainMenuActivity
 */
class NavigationManager private constructor() {
    
    companion object {
        @Volatile
        private var INSTANCE: NavigationManager? = null
        
        fun getInstance(): NavigationManager {
            return INSTANCE ?: synchronized(this) {
                val instance = NavigationManager()
                INSTANCE = instance
                instance
            }
        }
    }
    
    /**
     * Navega a la actividad especificada
     */
    fun navigateTo(context: Context, destination: NavigationDestination) {
        val intent = when (destination) {
            NavigationDestination.PROFILE -> Intent(context, ProfileActivity::class.java)
            NavigationDestination.SETTINGS -> Intent(context, SettingsActivity::class.java)
            NavigationDestination.TRANSACTIONS -> Intent(context, TransactionsActivity::class.java)
            NavigationDestination.REPORTS -> Intent(context, ReportsActivity::class.java)
            NavigationDestination.CATEGORIES -> Intent(context, CategoriesActivity::class.java)
            NavigationDestination.ACCOUNTS -> Intent(context, AccountsActivity::class.java)
            NavigationDestination.DEUDORES -> {
                Intent(context, PersonasRelacionadasActivity::class.java).apply {
                    putExtra("tipo", "DEUDOR")
                }
            }
            NavigationDestination.ACREEDORES -> {
                Intent(context, PersonasRelacionadasActivity::class.java).apply {
                    putExtra("tipo", "ACREEDOR")
                }
            }
            NavigationDestination.PRODUCTOS -> Intent(context, ProductosActivity::class.java)
            NavigationDestination.TARIFA_PAGO -> Intent(context, TarifaPagoActivity::class.java)
        }
        context.startActivity(intent)
    }
    
    /**
     * Obtiene el título de la actividad para mostrar en la UI
     */
    fun getTitle(destination: NavigationDestination): String {
        return when (destination) {
            NavigationDestination.PROFILE -> "Perfil"
            NavigationDestination.SETTINGS -> "Configuración"
            NavigationDestination.TRANSACTIONS -> "Transacciones"
            NavigationDestination.REPORTS -> "Informes"
            NavigationDestination.CATEGORIES -> "Categorías"
            NavigationDestination.ACCOUNTS -> "Cuentas"
            NavigationDestination.DEUDORES -> "Deudores"
            NavigationDestination.ACREEDORES -> "Acreedores"
            NavigationDestination.PRODUCTOS -> "Productos"
            NavigationDestination.TARIFA_PAGO -> "Tarifa de pago"
        }
    }
}

/**
 * Destinos de navegación disponibles
 */
enum class NavigationDestination {
    PROFILE,
    SETTINGS,
    TRANSACTIONS,
    REPORTS,
    CATEGORIES,
    ACCOUNTS,
    DEUDORES,
    ACREEDORES,
    PRODUCTOS,
    TARIFA_PAGO
}
