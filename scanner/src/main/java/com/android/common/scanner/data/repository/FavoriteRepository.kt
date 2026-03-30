package com.android.common.scanner.data.repository

import android.content.Context
import com.android.common.scanner.data.database.QRCodeDatabase
import com.android.common.scanner.data.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

class FavoriteRepository(context: Context) {

    private val dao = QRCodeDatabase.getInstance(context).favoriteDao()

    fun getAllFavorites(): Flow<List<FavoriteEntity>> = dao.getAllFavorites()

    suspend fun insert(content: String, barcodeType: Int, typeName: String, extraData: String? = null): Long {
        val entity = FavoriteEntity(
            content = content,
            barcodeType = barcodeType,
            typeName = typeName,
            extraData = extraData
        )
        return dao.insert(entity)
    }

    suspend fun delete(entity: FavoriteEntity) = dao.delete(entity)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteByContent(content: String) = dao.deleteByContent(content)

    suspend fun isFavorite(content: String): Boolean = dao.isFavorite(content)

    companion object {
        @Volatile
        private var INSTANCE: FavoriteRepository? = null

        fun getInstance(context: Context): FavoriteRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FavoriteRepository(context).also { INSTANCE = it }
            }
        }
    }
}
