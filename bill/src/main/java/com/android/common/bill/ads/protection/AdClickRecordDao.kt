package com.android.common.bill.ads.protection

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 广告点击记录 DAO
 */
@Dao
interface AdClickRecordDao {
    
    @Query("SELECT * FROM ad_click_records WHERE ad_identifier = :adIdentifier")
    suspend fun getRecord(adIdentifier: String): AdClickRecordEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: AdClickRecordEntity)
    
    @Query("UPDATE ad_click_records SET click_count = click_count + 1 WHERE ad_identifier = :adIdentifier")
    suspend fun incrementClickCount(adIdentifier: String): Int
    
    @Query("DELETE FROM ad_click_records WHERE ad_identifier = :adIdentifier")
    suspend fun deleteRecord(adIdentifier: String)
    
    @Query("DELETE FROM ad_click_records")
    suspend fun deleteAll()
    
    // 封禁状态相关
    @Query("SELECT * FROM ad_block_status WHERE id = 1")
    suspend fun getBlockStatus(): AdBlockStatusEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBlockStatus(status: AdBlockStatusEntity)
    
    @Query("DELETE FROM ad_block_status")
    suspend fun clearBlockStatus()
}
