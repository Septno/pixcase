package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pixcase.data.db.entity.ImageCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageCategoryDao {
    @Query("SELECT * FROM image_category ORDER BY usageCount DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ImageCategoryEntity>>

    @Query("SELECT * FROM image_category WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): ImageCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(category: ImageCategoryEntity): Long

    @Update
    suspend fun update(category: ImageCategoryEntity)

    @Delete
    suspend fun delete(category: ImageCategoryEntity)
}
