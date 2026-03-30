package com.android.common.scanner.data.repository

import android.content.Context
import com.android.common.scanner.data.database.QRCodeDatabase
import com.android.common.scanner.data.entity.ScanHistoryEntity
import kotlinx.coroutines.flow.Flow

class ScanHistoryRepository(context: Context) {

    private val dao = QRCodeDatabase.getInstance(context).scanHistoryDao()

    fun getAllHistory(): Flow<List<ScanHistoryEntity>> = dao.getAllHistory()

    suspend fun getAllHistoryList(): List<ScanHistoryEntity> = dao.getAllHistoryList()

    suspend fun insert(content: String, barcodeType: Int, typeName: String, extraData: String? = null): Long {
        val entity = ScanHistoryEntity(
            content = content,
            barcodeType = barcodeType,
            typeName = typeName,
            extraData = extraData
        )
        return dao.insert(entity)
    }

    suspend fun delete(entity: ScanHistoryEntity) = dao.delete(entity)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    companion object {
        @Volatile
        private var INSTANCE: ScanHistoryRepository? = null

        fun getInstance(context: Context): ScanHistoryRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScanHistoryRepository(context).also { INSTANCE = it }
            }
        }
    }
}
