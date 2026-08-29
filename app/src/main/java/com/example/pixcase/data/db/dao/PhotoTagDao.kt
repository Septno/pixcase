package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.PhotoTagCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoTagDao {

    @Query("SELECT tagId FROM photo_tag WHERE mediaId = :mediaId")
    fun observeTagIdsByMedia(mediaId: Long): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: PhotoTagCrossRef)

    @Query("DELETE FROM photo_tag WHERE tagId = :tagId AND mediaId = :mediaId")
    suspend fun delete(tagId: Long, mediaId: Long)
}