package cu.rge.cartera.data

import android.content.Context
import android.net.Uri
import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import android.util.Log
import androidx.room.Room
import cu.rge.cartera.data.model.User
import cu.rge.cartera.data.model.Transaction
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec
import java.security.MessageDigest
import java.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import java.nio.charset.StandardCharsets

class DataManager private constructor(private val context: Context) {
    private val database: AppDatabase
    private var currentUserId: String? = null

    init {
        database = AppDatabase.getInstance(context.applicationContext)
    }

    companion object {
        @Volatile
        private var INSTANCE: DataManager? = null

        fun getInstance(context: Context): DataManager {
            return INSTANCE ?: synchronized(this) {
                val instance = DataManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    fun setCurrentUser(email: String) {
        currentUserId = email
    }

    suspend fun getCurrentUser(): User {
        return withContext(Dispatchers.IO) {
            currentUserId?.let { email ->
                database.userDao().getUserByEmail(email)
            } ?: throw Exception("No hay usuario actual")
        }
    }

    suspend fun getTotalBalance(): Double {
        return withContext(Dispatchers.IO) {
            currentUserId?.let { email ->
                database.transactionDao().getTotalBalance(email)
            } ?: throw Exception("No hay usuario actual")
        }
    }

    suspend fun getTotalByType(type: String): Double {
        return withContext(Dispatchers.IO) {
            currentUserId?.let { email ->
                database.transactionDao().getTotalByType(email, type)
            } ?: throw Exception("No hay usuario actual")
        }
    }

    suspend fun logout() {
        withContext(Dispatchers.IO) {
            currentUserId = null
        }
    }

    // Función para derivar clave desde email y contraseña
    private fun deriveKey(email: String, password: String): SecretKeySpec {
        val combined = "$email:$password"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(combined.toByteArray(StandardCharsets.UTF_8))
        return SecretKeySpec(hash, "AES")
    }

    // Función para encriptar datos
    private fun encryptData(data: String, email: String, password: String): ByteArray {
        try {
            val key = deriveKey(email, password)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            
            // Generar IV único
            val iv = ByteArray(12)
            java.security.SecureRandom().nextBytes(iv)
            
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            
            val encryptedData = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
            
            // Combinar IV + datos encriptados
            return iv + encryptedData
        } catch (e: Exception) {
            Log.e("DataManager", "Error en encryptData", e)
            throw Exception("Error al encriptar datos: ${e.message}")
        }
    }

    // Función para desencriptar datos
    private fun decryptData(encryptedData: ByteArray, email: String, password: String): String {
        try {
            val key = deriveKey(email, password)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            
            // Separar IV (primeros 12 bytes) y datos encriptados
            val iv = encryptedData.sliceArray(0..11)
            val data = encryptedData.sliceArray(12 until encryptedData.size)
            
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            
            val decryptedBytes = cipher.doFinal(data)
            return String(decryptedBytes, StandardCharsets.UTF_8)
        } catch (e: javax.crypto.AEADBadTagException) {
            Log.e("DataManager", "Error de autenticación en decryptData", e)
            throw Exception("Credenciales incorrectas o archivo corrupto")
        } catch (e: Exception) {
            Log.e("DataManager", "Error en decryptData", e)
            throw Exception("Error al desencriptar datos: ${e.message}")
        }
    }

    suspend fun exportData() {
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implementar exportación de datos
                Log.d("DataManager", "Exportando datos...")
            } catch (e: Exception) {
                Log.e("DataManager", "Error al exportar datos", e)
                throw e
            }
        }
    }

    suspend fun importData() {
        withContext(Dispatchers.IO) {
            try {
                // TODO: Implementar importación de datos
                Log.d("DataManager", "Importando datos...")
            } catch (e: Exception) {
                Log.e("DataManager", "Error al importar datos", e)
                throw e
            }
        }
    }

    suspend fun exportData(file: File) {
        try {
            val users = database.userDao().getAllUsers()
            val accounts = database.accountDao().getAll()
            val categories = database.categoryDao().getAll()
            val currencyRates = database.currencyRateDao().getAllRates()
            val tarifasPago = database.tarifaPagoDao().getAll()
            val personasRelacionadas = database.personaRelacionadaDao().getAll()
            val abonosPersona = database.abonoPersonaDao().getAll()
            val productos = database.productoDao().getAll()
            val ventasProducto = database.ventaProductoDao().getAll()
            val defaultCurrency = database.currencyRateDao().getDefaultCurrency() ?: "CUP"
            val settings = listOf(
                JSONObject().apply {
                    put("key", "default_currency")
                    put("value", defaultCurrency)
                }
            )
            val transactions = users.flatMap { user ->
                database.transactionDao().getTransactionsByUser(user.email)
            }
            val root = JSONObject().apply {
                put("users", JSONArray(users.map { u ->
                    JSONObject().apply {
                        put("email", u.email)
                        put("password", u.password)
                        put("name", u.name)
                    }
                }))
                put("accounts", JSONArray(accounts.map { a ->
                    JSONObject().apply {
                        put("id", a.id)
                        put("name", a.name)
                        put("type", a.type)
                        put("initialBalance", a.initialBalance)
                        put("currency", a.currency)
                        put("exchangeRate", a.exchangeRate)
                    }
                }))
                put("categories", JSONArray(categories.map { c ->
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("parentId", c.parentId)
                        put("monthlyLimit", c.monthlyLimit)
                    }
                }))
                put("currencyRates", JSONArray(currencyRates.map { r ->
                    JSONObject().apply {
                        put("id", r.id)
                        put("fromCurrency", r.fromCurrency)
                        put("toCurrency", r.toCurrency)
                        put("rate", r.rate)
                    }
                }))
                put("tarifasPago", JSONArray(tarifasPago.map { t ->
                    JSONObject().apply {
                        put("id", t.id)
                        put("nombre", t.nombre)
                        put("porcentaje", t.porcentaje)
                        put("descripcion", t.descripcion)
                    }
                }))
                put("personasRelacionadas", JSONArray(personasRelacionadas.map { p ->
                    JSONObject().apply {
                        put("id", p.id)
                        put("nombre", p.nombre)
                        put("monto", p.monto)
                        put("moneda", p.moneda)
                        put("tipo", p.tipo)
                        put("descripcion", p.descripcion)
                        put("accountId", p.accountId)
                        put("activa", p.activa)
                        put("fecha", p.fecha?.time)
                    }
                }))
                put("abonosPersona", JSONArray(abonosPersona.map { a ->
                    JSONObject().apply {
                        put("id", a.id)
                        put("personaId", a.personaId)
                        put("monto", a.monto)
                        put("moneda", a.moneda)
                        put("fecha", a.fecha.time)
                        put("nota", a.nota)
                        put("transactionId", a.transactionId)
                    }
                }))
                put("productos", JSONArray(productos.map { p ->
                    JSONObject().apply {
                        put("id", p.id)
                        put("name", p.name)
                        put("currency", p.currency)
                        put("quantity", p.quantity)
                        put("unit", p.unit)
                        put("purchasePrice", p.purchasePrice)
                        put("sellingPrice", p.sellingPrice)
                        put("precioVentaDefinido", p.precioVentaDefinido)
                        put("cantidadTotalComprada", p.cantidadTotalComprada)
                    }
                }))
                put("ventasProducto", JSONArray(ventasProducto.map { v ->
                    JSONObject().apply {
                        put("id", v.id)
                        put("productoId", v.productoId)
                        put("cantidad", v.cantidad)
                        put("precioVenta", v.precioVenta)
                        put("moneda", v.moneda)
                        put("nota", v.nota)
                        put("fecha", v.fecha.time)
                        put("transactionId", v.transactionId)
                    }
                }))
                put("transactions", JSONArray(transactions.map { t ->
                    JSONObject().apply {
                        put("id", t.id)
                        put("userId", t.userId)
                        put("amount", t.amount)
                        put("description", t.description)
                        put("type", t.type)
                        put("category", t.category)
                        put("accountId", t.accountId)
                        put("date", t.date.time)
                    }
                }))
                put("settings", JSONArray(settings))
                
                // ===== METADATOS =====
                put("metadata", JSONObject().apply {
                    put("exportDate", System.currentTimeMillis())
                    put("version", "1.0")
                    put("userEmail", currentUserId ?: "")
                })
            }
            
            // Obtener usuario actual para encriptación
            val currentUser = getCurrentUser()
            
            // Encriptar los datos JSON
            val jsonString = root.toString(2)
            val encryptedData = encryptData(jsonString, currentUser.email, currentUser.password)
            
            // Guardar datos encriptados
            FileOutputStream(file).use { output ->
                output.write(encryptedData)
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error exporting data", e)
            throw e
        }
    }

    suspend fun importData(file: File, email: String, password: String) {
        try {
            withContext(Dispatchers.IO) {
                // Leer datos encriptados del archivo
                val encryptedData = FileInputStream(file).readBytes()
                
                // Desencriptar los datos
                val jsonString = decryptData(encryptedData, email, password)
                val root = JSONObject(jsonString)
                val usersArray = root.optJSONArray("users") ?: JSONArray()
                val accountsArray = root.optJSONArray("accounts") ?: JSONArray()
                val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
                val currencyRatesArray = root.optJSONArray("currencyRates") ?: JSONArray()
                val tarifasPagoArray = root.optJSONArray("tarifasPago") ?: JSONArray()
                val personasRelacionadasArray = root.optJSONArray("personasRelacionadas") ?: JSONArray()
                val abonosPersonaArray = root.optJSONArray("abonosPersona") ?: JSONArray()
                val productosArray = root.optJSONArray("productos") ?: JSONArray()
                val ventasProductoArray = root.optJSONArray("ventasProducto") ?: JSONArray()
                val transactionsArray = root.optJSONArray("transactions") ?: JSONArray()
                val settingsArray = root.optJSONArray("settings") ?: JSONArray()

                // Limpiar todas las tablas antes de restaurar
                database.clearAllTables()

                // Importar usuarios
                for (i in 0 until usersArray.length()) {
                    val o = usersArray.getJSONObject(i)
                    val user = cu.rge.cartera.data.model.User(
                        email = o.getString("email"),
                        password = o.getString("password"),
                        name = o.getString("name")
                    )
                    database.userDao().insertUser(user)
                }
                // Importar cuentas
                for (i in 0 until accountsArray.length()) {
                    val o = accountsArray.getJSONObject(i)
                    val account = cu.rge.cartera.data.model.Account(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        type = o.getString("type"),
                        initialBalance = o.getDouble("initialBalance"),
                        currency = o.getString("currency"),
                        exchangeRate = o.getDouble("exchangeRate")
                    )
                    database.accountDao().insert(account)
                }
                // Importar categorías
                for (i in 0 until categoriesArray.length()) {
                    val o = categoriesArray.getJSONObject(i)
                    val category = cu.rge.cartera.data.model.Category(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        parentId = if (o.isNull("parentId")) null else o.getLong("parentId"),
                        monthlyLimit = if (o.isNull("monthlyLimit")) null else o.getDouble("monthlyLimit")
                    )
                    database.categoryDao().insert(category)
                }
                // Importar tasas de cambio
                for (i in 0 until currencyRatesArray.length()) {
                    val o = currencyRatesArray.getJSONObject(i)
                    val rate = cu.rge.cartera.data.model.CurrencyRate(
                        id = o.optLong("id", 0),
                        fromCurrency = o.getString("fromCurrency"),
                        toCurrency = o.getString("toCurrency"),
                        rate = o.getDouble("rate")
                    )
                    database.currencyRateDao().insertRate(rate)
                }
                // Importar tarifas de pago
                for (i in 0 until tarifasPagoArray.length()) {
                    val o = tarifasPagoArray.getJSONObject(i)
                    val tarifa = cu.rge.cartera.data.model.TarifaPago(
                        id = o.optLong("id", 0),
                        nombre = o.getString("nombre"),
                        porcentaje = o.getDouble("porcentaje"),
                        descripcion = o.getString("descripcion")
                    )
                    database.tarifaPagoDao().insert(tarifa)
                }
                // Importar personas relacionadas
                for (i in 0 until personasRelacionadasArray.length()) {
                    val o = personasRelacionadasArray.getJSONObject(i)
                    val persona = cu.rge.cartera.data.model.PersonaRelacionada(
                        id = o.optLong("id", 0),
                        nombre = o.getString("nombre"),
                        monto = o.getDouble("monto"),
                        moneda = o.getString("moneda"),
                        tipo = o.getString("tipo"),
                        descripcion = if (o.isNull("descripcion")) null else o.getString("descripcion"),
                        accountId = if (o.has("accountId") && !o.isNull("accountId")) o.getLong("accountId") else null,
                        activa = o.optBoolean("activa", true),
                        fecha = if (o.isNull("fecha")) null else java.util.Date(o.getLong("fecha"))
                    )
                    database.personaRelacionadaDao().insert(persona)
                }
                // Importar abonos de persona
                for (i in 0 until abonosPersonaArray.length()) {
                    val o = abonosPersonaArray.getJSONObject(i)
                    val abono = cu.rge.cartera.data.model.AbonoPersona(
                        id = o.optLong("id", 0),
                        personaId = o.getLong("personaId"),
                        monto = o.getDouble("monto"),
                        moneda = o.getString("moneda"),
                        fecha = java.util.Date(o.getLong("fecha")),
                        nota = if (o.isNull("nota")) null else o.getString("nota"),
                        transactionId = if (o.isNull("transactionId")) null else o.getLong("transactionId")
                    )
                    database.abonoPersonaDao().insert(abono)
                }
                // Importar productos
                for (i in 0 until productosArray.length()) {
                    val o = productosArray.getJSONObject(i)
                    val producto = cu.rge.cartera.data.model.Producto(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        currency = o.getString("currency"),
                        quantity = o.getDouble("quantity"),
                        unit = o.getString("unit"),
                        purchasePrice = o.getDouble("purchasePrice"),
                        sellingPrice = o.getDouble("sellingPrice"),
                        precioVentaDefinido = o.optDouble("precioVentaDefinido", o.getDouble("sellingPrice")),
                        cantidadTotalComprada = o.optDouble("cantidadTotalComprada", o.getDouble("quantity"))
                    )
                    database.productoDao().insert(producto)
                }
                // Importar ventas de productos
                for (i in 0 until ventasProductoArray.length()) {
                    val o = ventasProductoArray.getJSONObject(i)
                    val venta = cu.rge.cartera.data.model.VentaProducto(
                        id = o.optLong("id", 0),
                        productoId = o.getLong("productoId"),
                        cantidad = o.getDouble("cantidad"),
                        precioVenta = o.getDouble("precioVenta"),
                        moneda = o.getString("moneda"),
                        nota = if (o.isNull("nota")) null else o.getString("nota"),
                        fecha = java.util.Date(o.getLong("fecha")),
                        transactionId = if (o.isNull("transactionId")) null else o.getLong("transactionId")
                    )
                    database.ventaProductoDao().insert(venta)
                }
                // Importar transacciones
                for (i in 0 until transactionsArray.length()) {
                    val o = transactionsArray.getJSONObject(i)
                    val transaction = cu.rge.cartera.data.model.Transaction(
                        id = o.optLong("id", 0),
                        userId = o.getString("userId"),
                        amount = o.getDouble("amount"),
                        description = o.getString("description"),
                        type = o.getString("type"),
                        category = o.getString("category"),
                        accountId = if (o.isNull("accountId")) null else o.getLong("accountId"),
                        date = java.util.Date(o.getLong("date"))
                    )
                    database.transactionDao().insert(transaction)
                }
                // Importar settings
                for (i in 0 until settingsArray.length()) {
                    val o = settingsArray.getJSONObject(i)
                    val key = o.getString("key")
                    val value = o.getString("value")
                    database.currencyRateDao().insertSetting(cu.rge.cartera.data.model.AppSettings(key, value))
                }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error importing data", e)
            throw e
        }
    }

    // Función sobrecargada para compatibilidad con backups antiguos (sin encriptar)
    suspend fun importData(file: File) {
        try {
            withContext(Dispatchers.IO) {
                // Detectar si el archivo está encriptado
                if (isBackupEncrypted(file)) {
                    throw Exception("El backup está encriptado. Por favor, usa importData(file, email, password) o importDataSmart(file, email, password).")
                }
                
                val jsonString = FileInputStream(file).bufferedReader().use { it.readText() }
                val root = JSONObject(jsonString)
                val usersArray = root.optJSONArray("users") ?: JSONArray()
                val accountsArray = root.optJSONArray("accounts") ?: JSONArray()
                val categoriesArray = root.optJSONArray("categories") ?: JSONArray()
                val currencyRatesArray = root.optJSONArray("currencyRates") ?: JSONArray()
                val tarifasPagoArray = root.optJSONArray("tarifasPago") ?: JSONArray()
                val personasRelacionadasArray = root.optJSONArray("personasRelacionadas") ?: JSONArray()
                val abonosPersonaArray = root.optJSONArray("abonosPersona") ?: JSONArray()
                val productosArray = root.optJSONArray("productos") ?: JSONArray()
                val ventasProductoArray = root.optJSONArray("ventasProducto") ?: JSONArray()
                val transactionsArray = root.optJSONArray("transactions") ?: JSONArray()
                val settingsArray = root.optJSONArray("settings") ?: JSONArray()

                // Limpiar todas las tablas antes de restaurar
                database.clearAllTables()

                // Importar usuarios
                for (i in 0 until usersArray.length()) {
                    val o = usersArray.getJSONObject(i)
                    val user = cu.rge.cartera.data.model.User(
                        email = o.getString("email"),
                        password = o.getString("password"),
                        name = o.getString("name")
                    )
                    database.userDao().insertUser(user)
                }
                // Importar cuentas
                for (i in 0 until accountsArray.length()) {
                    val o = accountsArray.getJSONObject(i)
                    val account = cu.rge.cartera.data.model.Account(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        type = o.getString("type"),
                        initialBalance = o.getDouble("initialBalance"),
                        currency = o.getString("currency"),
                        exchangeRate = o.getDouble("exchangeRate")
                    )
                    database.accountDao().insert(account)
                }
                // Importar categorías
                for (i in 0 until categoriesArray.length()) {
                    val o = categoriesArray.getJSONObject(i)
                    val category = cu.rge.cartera.data.model.Category(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        parentId = if (o.isNull("parentId")) null else o.getLong("parentId"),
                        monthlyLimit = if (o.isNull("monthlyLimit")) null else o.getDouble("monthlyLimit")
                    )
                    database.categoryDao().insert(category)
                }
                // Importar tasas de cambio
                for (i in 0 until currencyRatesArray.length()) {
                    val o = currencyRatesArray.getJSONObject(i)
                    val rate = cu.rge.cartera.data.model.CurrencyRate(
                        id = o.optLong("id", 0),
                        fromCurrency = o.getString("fromCurrency"),
                        toCurrency = o.getString("toCurrency"),
                        rate = o.getDouble("rate")
                    )
                    database.currencyRateDao().insertRate(rate)
                }
                // Importar tarifas de pago
                for (i in 0 until tarifasPagoArray.length()) {
                    val o = tarifasPagoArray.getJSONObject(i)
                    val tarifa = cu.rge.cartera.data.model.TarifaPago(
                        id = o.optLong("id", 0),
                        nombre = o.getString("nombre"),
                        porcentaje = o.getDouble("porcentaje"),
                        descripcion = o.getString("descripcion")
                    )
                    database.tarifaPagoDao().insert(tarifa)
                }
                // Importar personas relacionadas
                for (i in 0 until personasRelacionadasArray.length()) {
                    val o = personasRelacionadasArray.getJSONObject(i)
                    val persona = cu.rge.cartera.data.model.PersonaRelacionada(
                        id = o.optLong("id", 0),
                        nombre = o.getString("nombre"),
                        monto = o.getDouble("monto"),
                        moneda = o.getString("moneda"),
                        tipo = o.getString("tipo"),
                        descripcion = if (o.isNull("descripcion")) null else o.getString("descripcion"),
                        accountId = if (o.has("accountId") && !o.isNull("accountId")) o.getLong("accountId") else null,
                        activa = o.optBoolean("activa", true),
                        fecha = if (o.isNull("fecha")) null else java.util.Date(o.getLong("fecha"))
                    )
                    database.personaRelacionadaDao().insert(persona)
                }
                // Importar abonos de persona
                for (i in 0 until abonosPersonaArray.length()) {
                    val o = abonosPersonaArray.getJSONObject(i)
                    val abono = cu.rge.cartera.data.model.AbonoPersona(
                        id = o.optLong("id", 0),
                        personaId = o.getLong("personaId"),
                        monto = o.getDouble("monto"),
                        moneda = o.getString("moneda"),
                        fecha = java.util.Date(o.getLong("fecha")),
                        nota = if (o.isNull("nota")) null else o.getString("nota"),
                        transactionId = if (o.isNull("transactionId")) null else o.getLong("transactionId")
                    )
                    database.abonoPersonaDao().insert(abono)
                }
                // Importar productos
                for (i in 0 until productosArray.length()) {
                    val o = productosArray.getJSONObject(i)
                    val producto = cu.rge.cartera.data.model.Producto(
                        id = o.optLong("id", 0),
                        name = o.getString("name"),
                        currency = o.getString("currency"),
                        quantity = o.getDouble("quantity"),
                        unit = o.getString("unit"),
                        purchasePrice = o.getDouble("purchasePrice"),
                        sellingPrice = o.getDouble("sellingPrice"),
                        precioVentaDefinido = o.optDouble("precioVentaDefinido", o.getDouble("sellingPrice")),
                        cantidadTotalComprada = o.optDouble("cantidadTotalComprada", o.getDouble("quantity"))
                    )
                    database.productoDao().insert(producto)
                }
                // Importar ventas de productos
                for (i in 0 until ventasProductoArray.length()) {
                    val o = ventasProductoArray.getJSONObject(i)
                    val venta = cu.rge.cartera.data.model.VentaProducto(
                        id = o.optLong("id", 0),
                        productoId = o.getLong("productoId"),
                        cantidad = o.getDouble("cantidad"),
                        precioVenta = o.getDouble("precioVenta"),
                        moneda = o.getString("moneda"),
                        nota = if (o.isNull("nota")) null else o.getString("nota"),
                        fecha = java.util.Date(o.getLong("fecha")),
                        transactionId = if (o.isNull("transactionId")) null else o.getLong("transactionId")
                    )
                    database.ventaProductoDao().insert(venta)
                }
                // Importar transacciones
                for (i in 0 until transactionsArray.length()) {
                    val o = transactionsArray.getJSONObject(i)
                    val transaction = cu.rge.cartera.data.model.Transaction(
                        id = o.optLong("id", 0),
                        userId = o.getString("userId"),
                        amount = o.getDouble("amount"),
                        description = o.getString("description"),
                        type = o.getString("type"),
                        category = o.getString("category"),
                        accountId = if (o.isNull("accountId")) null else o.getLong("accountId"),
                        date = java.util.Date(o.getLong("date"))
                    )
                    database.transactionDao().insert(transaction)
                }
                // Importar settings
                for (i in 0 until settingsArray.length()) {
                    val o = settingsArray.getJSONObject(i)
                    val key = o.getString("key")
                    val value = o.getString("value")
                    database.currencyRateDao().insertSetting(cu.rge.cartera.data.model.AppSettings(key, value))
                }
            }
        } catch (e: Exception) {
            Log.e("DataManager", "Error importing data", e)
            throw e
        }
    }

    // Función para detectar si un backup está encriptado
    private fun isBackupEncrypted(file: File): Boolean {
        return try {
            val bytes = FileInputStream(file).readBytes()
            // Si los primeros bytes no son '{' (JSON), asumimos que está encriptado
            bytes.isNotEmpty() && bytes[0] != '{'.code.toByte()
        } catch (e: Exception) {
            false
        }
    }

    // Función inteligente que detecta automáticamente si el backup está encriptado
    suspend fun importDataSmart(file: File, email: String? = null, password: String? = null) {
        if (isBackupEncrypted(file)) {
            if (email == null || password == null) {
                throw Exception("Este backup está encriptado. Se requieren email y contraseña para desencriptarlo.")
            }
            importData(file, email, password)
        } else {
            importData(file)
        }
    }
} 