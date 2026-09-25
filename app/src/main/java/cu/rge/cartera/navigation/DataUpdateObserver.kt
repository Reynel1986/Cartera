package cu.rge.cartera.navigation

import java.util.concurrent.CopyOnWriteArrayList

/**
 * Patrón Observer para notificar cambios en los datos
 * Permite que múltiples componentes se actualicen automáticamente
 * cuando hay cambios en la base de datos
 */
class DataUpdateObserver {
    
    companion object {
        @Volatile
        private var INSTANCE: DataUpdateObserver? = null
        
        fun getInstance(): DataUpdateObserver {
            return INSTANCE ?: synchronized(this) {
                val instance = DataUpdateObserver()
                INSTANCE = instance
                instance
            }
        }
    }
    
    private val observers = CopyOnWriteArrayList<DataUpdateListener>()
    
    /**
     * Registra un observador para recibir notificaciones de cambios
     */
    fun addObserver(observer: DataUpdateListener) {
        if (!observers.contains(observer)) {
            observers.add(observer)
        }
    }
    
    /**
     * Desregistra un observador
     */
    fun removeObserver(observer: DataUpdateListener) {
        observers.remove(observer)
    }
    
    /**
     * Notifica a todos los observadores sobre un cambio en los datos
     */
    fun notifyDataChanged(updateType: DataUpdateType) {
        observers.forEach { observer ->
            try {
                observer.onDataChanged(updateType)
            } catch (e: Exception) {
                // Log error but don't crash the app
                android.util.Log.e("DataUpdateObserver", "Error notifying observer", e)
            }
        }
    }
    
    /**
     * Notifica sobre cambios específicos en cuentas
     */
    fun notifyAccountsChanged() {
        notifyDataChanged(DataUpdateType.ACCOUNTS)
    }
    
    /**
     * Notifica sobre cambios específicos en transacciones
     */
    fun notifyTransactionsChanged() {
        notifyDataChanged(DataUpdateType.TRANSACTIONS)
    }
    
    /**
     * Notifica sobre cambios específicos en personas relacionadas
     */
    fun notifyPersonasChanged() {
        notifyDataChanged(DataUpdateType.PERSONAS)
    }
    
    /**
     * Notifica sobre cambios específicos en productos
     */
    fun notifyProductosChanged() {
        notifyDataChanged(DataUpdateType.PRODUCTOS)
    }
    
    /**
     * Notifica sobre cambios generales que requieren recarga completa
     */
    fun notifyGeneralUpdate() {
        notifyDataChanged(DataUpdateType.GENERAL)
    }
}

/**
 * Interfaz para recibir notificaciones de cambios en los datos
 */
interface DataUpdateListener {
    fun onDataChanged(updateType: DataUpdateType)
}

/**
 * Tipos de actualizaciones de datos
 */
enum class DataUpdateType {
    ACCOUNTS,      // Cambios en cuentas
    TRANSACTIONS,  // Cambios en transacciones
    PERSONAS,      // Cambios en personas relacionadas
    PRODUCTOS,     // Cambios en productos
    GENERAL        // Cambio general que requiere recarga completa
}
