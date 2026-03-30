package com.android.common.scanner.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.android.common.scanner.base.BaseModel
import com.android.common.scanner.data.entity.ScanHistoryEntity
import com.android.common.scanner.data.repository.ScanHistoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ScanHistoryModel : BaseModel() {

    private lateinit var historyRepository: ScanHistoryRepository

    private val _historyList = MutableStateFlow<List<ScanHistoryEntity>>(emptyList())
    val historyList: StateFlow<List<ScanHistoryEntity>> = _historyList

    fun init(context: Context) {
        historyRepository = ScanHistoryRepository.getInstance(context)
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            historyRepository.getAllHistory().collectLatest { list ->
                _historyList.value = list
            }
        }
    }

    fun deleteItem(entity: ScanHistoryEntity) {
        viewModelScope.launch {
            historyRepository.delete(entity)
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            historyRepository.deleteAll()
        }
    }
}
