package cu.rge.cartera.data

import android.content.Context
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import cu.rge.cartera.data.dao.TransactionDao
import cu.rge.cartera.data.dao.UserDao
import cu.rge.cartera.data.model.Transaction
import cu.rge.cartera.data.model.User
import cu.rge.cartera.data.model.Category
import cu.rge.cartera.data.dao.CategoryDao
import cu.rge.cartera.data.model.Account
import cu.rge.cartera.data.dao.AccountDao
import cu.rge.cartera.data.model.CurrencyRate
import cu.rge.cartera.data.model.AppSettings
import cu.rge.cartera.data.dao.CurrencyRateDao
import cu.rge.cartera.data.model.PersonaRelacionada
import cu.rge.cartera.data.dao.PersonaRelacionadaDao
import cu.rge.cartera.data.model.AbonoPersona
import cu.rge.cartera.data.dao.AbonoPersonaDao
import cu.rge.cartera.data.model.Producto
import cu.rge.cartera.data.dao.ProductoDao
import cu.rge.cartera.data.model.VentaProducto
import cu.rge.cartera.data.dao.VentaProductoDao
import cu.rge.cartera.data.model.TarifaPago
import cu.rge.cartera.data.dao.TarifaPagoDao
import cu.rge.cartera.data.model.CorteProducto
import cu.rge.cartera.data.dao.CorteProductoDao

@Database(
    entities = [User::class, Transaction::class, Category::class, Account::class, CurrencyRate::class, AppSettings::class, PersonaRelacionada::class, AbonoPersona::class, Producto::class, VentaProducto::class, TarifaPago::class, CorteProducto::class],
    version = 17,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun currencyRateDao(): CurrencyRateDao
    abstract fun personaRelacionadaDao(): PersonaRelacionadaDao
    abstract fun abonoPersonaDao(): AbonoPersonaDao
    abstract fun productoDao(): ProductoDao
    abstract fun ventaProductoDao(): VentaProductoDao
    abstract fun tarifaPagoDao(): TarifaPagoDao
    abstract fun corteProductoDao(): CorteProductoDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE abonos_persona ADD COLUMN transactionId INTEGER")
                    }
                }
                val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `productos` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL, `precio` REAL NOT NULL, `moneda` TEXT NOT NULL, `descripcion` TEXT)")
                    }
                }
                val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        // Crear tabla temporal con nueva estructura
                        database.execSQL("CREATE TABLE `productos_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `currency` TEXT NOT NULL, `quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `purchasePrice` REAL NOT NULL, `sellingPrice` REAL NOT NULL)")
                        
                        // Copiar datos existentes con valores por defecto para nuevos campos
                        database.execSQL("INSERT INTO productos_new (id, name, currency, quantity, unit, purchasePrice, sellingPrice) SELECT id, nombre, moneda, 0.0, 'unidades', precio, precio FROM productos")
                        
                        // Eliminar tabla antigua y renombrar la nueva
                        database.execSQL("DROP TABLE productos")
                        database.execSQL("ALTER TABLE productos_new RENAME TO productos")
                    }
                }
                val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `ventas_producto` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productoId` INTEGER NOT NULL, `cantidad` REAL NOT NULL, `precioVenta` REAL NOT NULL, `moneda` TEXT NOT NULL, `nota` TEXT, `fecha` INTEGER NOT NULL, `transactionId` INTEGER)")
                    }
                }
                val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `tarifa_pago` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nombre` TEXT NOT NULL, `porcentaje` REAL NOT NULL, `descripcion` TEXT NOT NULL)")
                    }
                }
                val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE productos ADD COLUMN precioVentaDefinido REAL NOT NULL DEFAULT 0.0")
                    }
                }
                val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE personas_relacionadas ADD COLUMN activa INTEGER NOT NULL DEFAULT 1")
                    }
                }
                val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE productos ADD COLUMN cantidadTotalComprada REAL NOT NULL DEFAULT 0.0")
                    }
                }
                val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE personas_relacionadas ADD COLUMN accountId INTEGER")
                    }
                }
                val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("ALTER TABLE productos ADD COLUMN cuentaGananciaId INTEGER")
                        database.execSQL("ALTER TABLE productos ADD COLUMN cuentaVentaId INTEGER")
                    }
                }
                val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
                    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                        database.execSQL("CREATE TABLE IF NOT EXISTS `cortes_producto` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `productoId` INTEGER NOT NULL, `fechaCorte` INTEGER NOT NULL, `cantidadTotalComprada` REAL NOT NULL, `purchasePrice` REAL NOT NULL, `sellingPrice` REAL NOT NULL, `precioVentaDefinido` REAL NOT NULL, `currency` TEXT NOT NULL, `unit` TEXT NOT NULL)")
                    }
                }
                val instance = androidx.room.Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cartera-db"
                )
                .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
} 