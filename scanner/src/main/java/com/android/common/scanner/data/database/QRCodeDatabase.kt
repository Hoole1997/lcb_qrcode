package com.android.common.scanner.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.android.common.scanner.data.dao.FavoriteDao
import com.android.common.scanner.data.dao.ScanHistoryDao
import com.android.common.scanner.data.entity.FavoriteEntity
import com.android.common.scanner.data.entity.ScanHistoryEntity

@Database(
    entities = [ScanHistoryEntity::class, FavoriteEntity::class],
    version = 3,
    exportSchema = false
)
abstract class QRCodeDatabase : RoomDatabase() {

    abstract fun scanHistoryDao(): ScanHistoryDao
    abstract fun favoriteDao(): FavoriteDao

    companion object {
        private const val DATABASE_NAME = "qrcode_database"

        @Volatile
        private var INSTANCE: QRCodeDatabase? = null
        
        // Migration from version 1 to 2: add extraData column to scan_history
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE scan_history ADD COLUMN extraData TEXT")
            }
        }
        
        // Migration from version 2 to 3: add extraData column to favorites
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE favorites ADD COLUMN extraData TEXT")
            }
        }

        fun getInstance(context: Context): QRCodeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QRCodeDatabase::class.java,
                    DATABASE_NAME
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }
    }
}
