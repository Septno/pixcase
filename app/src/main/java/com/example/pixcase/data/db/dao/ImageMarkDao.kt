package com.example.pixcase.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pixcase.data.db.entity.ImageMarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageMarkDao {
    @Query("SELECT * FROM image_mark WHERE mediaId = :mediaId")
    fun observeByMedia(mediaId: Long): Flow<List<ImageMarkEntity>>

    /**
     * 模糊匹配:为搜索"印象"提供命中列表。
     * LIKE 查询会用上 markNameNormalized 索引(前缀匹配 * 关键字 * 的 BLOB 扫描,
     * 大库建议结合其他字段降级到 FTS4/5,阶段 8 再考虑)。
     */
    @Query("SELECT DISTINCT mediaId FROM image_mark WHERE markNameNormalized LIKE '%' || :keyword || '%' LIMIT 500")
    suspend fun searchByKeyword(keyword: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mark: ImageMarkEntity)

    @Query("DELETE FROM image_mark WHERE mediaId = :mediaId AND markNameNormalized = :normalized")
    suspend fun delete(mediaId: Long, normalized: String)
}
