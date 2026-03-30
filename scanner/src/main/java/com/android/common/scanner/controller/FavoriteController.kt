package com.android.common.scanner.controller

import android.content.Context
import android.widget.Toast
import com.android.common.scanner.R
import com.android.common.scanner.data.entity.ScanHistoryEntity
import com.android.common.scanner.data.repository.FavoriteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 收藏控制器，统一管理收藏相关操作
 */
object FavoriteController {

    private var repository: FavoriteRepository? = null

    private fun getRepository(context: Context): FavoriteRepository {
        return repository ?: FavoriteRepository.getInstance(context).also { repository = it }
    }

    /**
     * 检查内容是否已收藏
     */
    suspend fun isFavorite(context: Context, content: String): Boolean {
        return withContext(Dispatchers.IO) {
            getRepository(context).isFavorite(content)
        }
    }

    /**
     * 从收藏中移除
     */
    suspend fun removeFromFavorites(
        context: Context,
        content: String,
        showToast: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            getRepository(context).deleteByContent(content)
        }
        if (showToast) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.qrcode_removed_from_favorites, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 从 ScanHistoryEntity 添加到收藏（使用模型传递，入库时提取字段）
     */
    suspend fun addToFavorites(
        context: Context,
        entity: ScanHistoryEntity,
        showToast: Boolean = true
    ) {
        withContext(Dispatchers.IO) {
            getRepository(context).insert(
                entity.content,
                entity.barcodeType,
                entity.typeName,
                entity.extraData
            )
        }
        if (showToast) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.qrcode_added_to_favorites, Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 从 ScanHistoryEntity 切换收藏状态（使用模型传递）
     */
    suspend fun toggleFavorite(
        context: Context,
        entity: ScanHistoryEntity,
        showToast: Boolean = true
    ): Boolean {
        val isFavorited = isFavorite(context, entity.content)
        return if (isFavorited) {
            removeFromFavorites(context, entity.content, showToast)
            false
        } else {
            addToFavorites(context, entity, showToast)
            true
        }
    }
}
