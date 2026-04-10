package com.android.common.bill.ads.protection

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * 广告点击保护数据库
 */
@Database(
    entities = [AdClickRecordEntity::class, AdBlockStatusEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AdClickProtectionDatabase : RoomDatabase() {
    
    abstract fun adClickRecordDao(): AdClickRecordDao
    
    companion object {
        private const val DATABASE_NAME = "ad_click_protection.db"
        
        @Volatile
        private var instance: AdClickProtectionDatabase? = null
        
        fun getInstance(context: Context): AdClickProtectionDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AdClickProtectionDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
