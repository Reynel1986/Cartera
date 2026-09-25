package cu.rge.cartera.data.dao

import androidx.room.*
import cu.rge.cartera.data.model.CurrencyRate
import cu.rge.cartera.data.model.AppSettings

@Dao
interface CurrencyRateDao {
    @Query("SELECT * FROM currency_rates")
    suspend fun getAllRates(): List<CurrencyRate>

    @Query("SELECT * FROM currency_rates WHERE fromCurrency = :fromCurrency AND toCurrency = :toCurrency")
    suspend fun getRate(fromCurrency: String, toCurrency: String): CurrencyRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: CurrencyRate): Long

    @Update
    suspend fun updateRate(rate: CurrencyRate)

    @Delete
    suspend fun deleteRate(rate: CurrencyRate)

    @Query("SELECT value FROM app_settings WHERE `key` = 'default_currency'")
    suspend fun getDefaultCurrency(): String?

    @Query("SELECT * FROM currency_rates WHERE id = :id LIMIT 1")
    suspend fun getRateById(id: Long): CurrencyRate?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSetting(setting: AppSettings)

    @Query("SELECT value FROM app_settings WHERE `key` = :key")
    suspend fun getSetting(key: String): String?

    /**
     * Obtiene la tasa de cambio entre dos monedas, buscando la directa, la inversa,
     * o calculando a través de CUP si es necesario.
     */
    suspend fun getSmartRate(fromCurrency: String, toCurrency: String): Double? {
        if (fromCurrency == toCurrency) return 1.0
        // 1. Buscar tasa directa
        val direct = getRate(fromCurrency, toCurrency)?.rate
        if (direct != null && direct > 0) return direct
        // 2. Buscar tasa inversa
        val inverse = getRate(toCurrency, fromCurrency)?.rate
        if (inverse != null && inverse > 0) return 1.0 / inverse
        // 3. Buscar a través de CUP como moneda común
        val cup = "CUP"
        val fromToCup = if (fromCurrency == cup) 1.0 else getRate(fromCurrency, cup)?.rate
        val toToCup = if (toCurrency == cup) 1.0 else getRate(toCurrency, cup)?.rate
        if (fromToCup != null && fromToCup > 0 && toToCup != null && toToCup > 0) {
            // Ejemplo: de MLC a USD: (MLC->CUP) / (USD->CUP)
            return fromToCup / toToCup
        }
        // No se encontró ninguna tasa válida
        return null
    }
} 