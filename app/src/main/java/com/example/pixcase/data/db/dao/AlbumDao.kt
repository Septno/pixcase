package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.pixcase.data.db.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album WHERE isSystem = 0 ORDER BY updatedAt DESC")
    fun observeUserAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun findById(id: Long): AlbumEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(album: AlbumEntity): Long

    @Update
    suspend fun update(album: AlbumEntity)

    @Delete
    suspend fun delete(album: AlbumEntity)

    /** 手动指定封面;同时刷新 updatedAt 让相册列表重排。 */
    @Query("UPDATE album SET coverMediaId = :coverMediaId, updatedAt = :now WHERE id = :albumId")
    suspend fun setCover(albumId: Long, coverMediaId: Long, now: Long)

    /** 相册里已无照片时清空封面。 */
    @Query("UPDATE album SET coverMediaId = NULL, updatedAt = :now WHERE id = :albumId")
    suspend fun clearCover(albumId: Long, now: Long)
}
