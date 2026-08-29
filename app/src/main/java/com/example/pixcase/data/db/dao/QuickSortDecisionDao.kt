package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.QuickSortDecisionEntity

@Dao
interface QuickSortDecisionDao {
    @Query("SELECT mediaId FROM quick_sort_decision WHERE sessionAlbumId = :sessionAlbumId")
    suspend fun processedMediaIds(sessionAlbumId: Long): List<Long>

    @Query("DELETE FROM quick_sort_decision WHERE sessionAlbumId = :sessionAlbumId")
    suspend fun clearSession(sessionAlbumId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(decision: QuickSortDecisionEntity)
}
