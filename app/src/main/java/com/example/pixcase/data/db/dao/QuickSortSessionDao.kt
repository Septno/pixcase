package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickSortSessionDao {
    @Query("SELECT * FROM quick_sort_session WHERE albumId = :albumId")
    fun observeByAlbum(albumId: Long): Flow<QuickSortSessionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(session: QuickSortSessionEntity)

    @Update
    suspend fun update(session: QuickSortSessionEntity)

    @Query("DELETE FROM quick_sort_session WHERE albumId = :albumId")
    suspend fun delete(albumId: Long)
}
