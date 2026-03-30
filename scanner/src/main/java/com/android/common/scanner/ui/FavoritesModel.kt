package com.android.common.scanner.ui

import android.content.Context
import androidx.lifecycle.viewModelScope
import com.android.common.scanner.base.BaseModel
import com.android.common.scanner.data.entity.FavoriteEntity
import com.android.common.scanner.data.repository.FavoriteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FavoritesModel : BaseModel() {

    private lateinit var repository: FavoriteRepository

    private val _favoritesList = MutableStateFlow<List<FavoriteEntity>>(emptyList())
    val favoritesList: StateFlow<List<FavoriteEntity>> = _favoritesList

    fun init(context: Context) {
        repository = FavoriteRepository.getInstance(context)
    }

    fun loadFavorites() {
        viewModelScope.launch {
            repository.getAllFavorites().collectLatest { list ->
                _favoritesList.value = list
            }
        }
    }

    fun deleteItem(entity: FavoriteEntity) {
        viewModelScope.launch {
            repository.delete(entity)
        }
    }
}
