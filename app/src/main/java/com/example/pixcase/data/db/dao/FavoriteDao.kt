package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT mediaId FROM favorite ORDER BY addedAt DESC")
    fun observeAllIds(): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)
}
