package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.PhotoTagCrossRef
import com.example.pixcase.data.db.entity.PhotoTagRow
import com.example.pixcase.data.db.entity.TagUsageRow
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoTagDao {
    @Query("SELECT tagId FROM photo_tag WHERE mediaId = :mediaId")
    fun observeTagIdsByMedia(mediaId: Long): Flow<List<Long>>

    /** 一批照片的全部标签关联(打标签对话框计算「同时打在所有选中照片上」的交集)。 */
    @Query("SELECT mediaId, tagId FROM photo_tag WHERE mediaId IN (:mediaIds)")
    suspend fun rowsForMedia(mediaIds: Collection<Long>): List<PhotoTagRow>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ref: PhotoTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(refs: List<PhotoTagCrossRef>)

    @Query("DELETE FROM photo_tag WHERE tagId = :tagId AND mediaId = :mediaId")
    suspend fun delete(tagId: Long, mediaId: Long)

    /** 从一批照片上摘掉某个标签(取消勾选)。 */
    @Query("DELETE FROM photo_tag WHERE tagId = :tagId AND mediaId IN (:mediaIds)")
    suspend fun deleteForMedia(tagId: Long, mediaIds: Collection<Long>)

    /** 清掉一批照片的所有标签关联(MediaStore 真删后的孤儿清理,计划陷阱 3)。 */
    @Query("DELETE FROM photo_tag WHERE mediaId IN (:mediaIds)")
    suspend fun deleteByMediaIds(mediaIds: Collection<Long>)

    /** 删除标签时清掉它的全部关联(无外键级联,见计划陷阱 3)。 */
    @Query("DELETE FROM photo_tag WHERE tagId = :tagId")
    suspend fun deleteByTagId(tagId: Long)

    /** 全表去重 mediaId(孤儿清扫兜底用)。 */
    @Query("SELECT DISTINCT mediaId FROM photo_tag")
    suspend fun allMediaIds(): List<Long>

    /** 一批标签命中的全部照片(搜索:按标签名命中后反查成员)。 */
    @Query("SELECT DISTINCT mediaId FROM photo_tag WHERE tagId IN (:tagIds)")
    suspend fun mediaIdsForTags(tagIds: Collection<Long>): List<Long>

    /** 标签使用量聚合(搜索建议按高频排序)。 */
    @Query("SELECT tagId, COUNT(mediaId) AS usageCount FROM photo_tag GROUP BY tagId")
    suspend fun tagUsageCounts(): List<TagUsageRow>
}
