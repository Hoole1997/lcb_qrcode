package com.android.common.scanner.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.android.common.scanner.data.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {

    @Insert
    suspend fun insert(entity: FavoriteEntity): Long

    @Query("SELECT * FROM favorites ORDER BY addTime DESC")
    fun getAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE id = :id")
    suspend fun getById(id: Long): FavoriteEntity?

    @Query("SELECT * FROM favorites WHERE content = :content LIMIT 1")
    suspend fun getByContent(content: String): FavoriteEntity?

    @Delete
    suspend fun delete(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM favorites WHERE content = :content")
    suspend fun deleteByContent(content: String)

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE content = :content)")
    suspend fun isFavorite(content: String): Boolean
}
