package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.QuickSortSessionTargetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickSortSessionTargetDao {
    @Query("SELECT * FROM quick_sort_session_target WHERE sessionAlbumId = :sessionAlbumId ORDER BY position")
    fun observeForSession(sessionAlbumId: Long): Flow<List<QuickSortSessionTargetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(targets: List<QuickSortSessionTargetEntity>)

    @Query("DELETE FROM quick_sort_session_target WHERE sessionAlbumId = :sessionAlbumId")
    suspend fun deleteForSession(sessionAlbumId: Long)
}
