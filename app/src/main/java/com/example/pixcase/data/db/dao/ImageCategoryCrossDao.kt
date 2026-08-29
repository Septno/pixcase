package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.ImageCategoryCrossRef

@Dao
interface ImageCategoryCrossDao {
    @Query("SELECT categoryId FROM image_category_cross WHERE mediaId = :mediaId")
    suspend fun categoryIdsByMedia(mediaId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: ImageCategoryCrossRef)

    @Query("DELETE FROM image_category_cross WHERE categoryId = :categoryId AND mediaId = :mediaId")
    suspend fun delete(categoryId: Long, mediaId: Long)
}
