package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.AlbumPhotoCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumPhotoDao {
    @Query("SELECT mediaId FROM album_photo WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun observeMediaIdsByAlbum(albumId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM album_photo WHERE mediaId = :mediaId")
    suspend fun countAlbumsContaining(mediaId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: AlbumPhotoCrossRef)

    @Query("DELETE FROM album_photo WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun delete(albumId: Long, mediaId: Long)
}
