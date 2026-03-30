package com.android.common.scanner.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,          // 显示内容（QR/条码内容 或 PDF文件名）
    val barcodeType: Int,
    val typeName: String,
    val addTime: Long = System.currentTimeMillis(),
    val extraData: String? = null // 额外数据（PDF的URI等）
)
