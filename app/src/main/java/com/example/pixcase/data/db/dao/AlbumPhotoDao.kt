package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.AlbumCountRow
import com.example.pixcase.data.db.entity.AlbumPhotoCrossRef
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumPhotoDao {
    @Query("SELECT mediaId FROM album_photo WHERE albumId = :albumId ORDER BY addedAt DESC")
    fun observeMediaIdsByAlbum(albumId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM album_photo WHERE albumId = :albumId")
    suspend fun countInAlbum(albumId: Long): Int

    /** 相册封面的自动来源:最近加入该相册的一张。 */
    @Query("SELECT mediaId FROM album_photo WHERE albumId = :albumId ORDER BY addedAt DESC LIMIT 1")
    suspend fun latestMediaIdInAlbum(albumId: Long): Long?

    /** 相册条数一次查全(GROUP BY),相册列表页用。 */
    @Query("SELECT albumId, COUNT(*) AS photoCount FROM album_photo GROUP BY albumId")
    fun observeCountsByAlbum(): Flow<List<AlbumCountRow>>

    @Query("SELECT albumId FROM album_photo WHERE mediaId = :mediaId")
    suspend fun albumIdsOf(mediaId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: AlbumPhotoCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(refs: List<AlbumPhotoCrossRef>)

    @Query("DELETE FROM album_photo WHERE albumId = :albumId AND mediaId = :mediaId")
    suspend fun delete(albumId: Long, mediaId: Long)

    /** 删光某个相册的全部关联(删除相册时)。 */
    @Query("DELETE FROM album_photo WHERE albumId = :albumId")
    suspend fun deleteAllInAlbum(albumId: Long)

    /** 清掉一批照片的所有相册关联(MediaStore 真删后的孤儿清理,计划陷阱 3)。 */
    @Query("DELETE FROM album_photo WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: Collection<Long>)

    /** 全表去重 mediaId(孤儿清扫兜底用)。 */
    @Query("SELECT DISTINCT mediaId FROM album_photo")
    suspend fun allMediaIds(): List<Long>

    /** 照片所属相册数(快速整理上划删除的双重语义判定:仅 1 个 → 真删除,否则仅解绑)。 */
    @Query("SELECT COUNT(DISTINCT albumId) FROM album_photo WHERE mediaId = :mediaId")
    suspend fun countAlbumsOf(mediaId: Long): Int
}
