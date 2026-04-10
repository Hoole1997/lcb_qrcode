package com.android.common.bill.ads.protection

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 广告点击记录实体
 */
@Entity(tableName = "ad_click_records")
data class AdClickRecordEntity(
    @PrimaryKey
    @ColumnInfo(name = "ad_identifier")
    val adIdentifier: String,
    
    @ColumnInfo(name = "click_count")
    val clickCount: Int = 0
)

/**
 * 广告封禁状态实体
 */
@Entity(tableName = "ad_block_status")
data class AdBlockStatusEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,
    
    @ColumnInfo(name = "is_blocked")
    val isBlocked: Boolean = false,
    
    @ColumnInfo(name = "blocked_date")
    val blockedDate: String = "",
    
    @ColumnInfo(name = "last_reset_date")
    val lastResetDate: String = ""
)
