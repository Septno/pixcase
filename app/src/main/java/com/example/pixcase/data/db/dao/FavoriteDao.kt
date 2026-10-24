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

    /** 全表读取(收藏反向同步:需按 addedAt 判断新近写入,避开与 createFavoriteRequest 的时序竞态)。 */
    @Query("SELECT * FROM favorite")
    suspend fun getAll(): List<FavoriteEntity>

    @Query("SELECT mediaId FROM favorite WHERE mediaId IN (:mediaIds)")
    fun observeFavoritesOf(mediaIds: Collection<Long>): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE mediaId = :mediaId)")
    suspend fun isFavorite(mediaId: Long): Boolean

    /** 批量查询选中项的收藏状态(多选操作栏据此判断收藏/取消收藏方向)。 */
    @Query("SELECT mediaId FROM favorite WHERE mediaId IN (:mediaIds)")
    suspend fun favoritesOf(mediaIds: Collection<Long>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(favorite: FavoriteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(favorites: List<FavoriteEntity>)

    @Query("DELETE FROM favorite WHERE mediaId = :mediaId")
    suspend fun delete(mediaId: Long)

    @Query("DELETE FROM favorite WHERE mediaId IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: Collection<Long>)
}
