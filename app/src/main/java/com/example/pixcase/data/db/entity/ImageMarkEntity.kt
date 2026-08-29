package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 印象标记(工具二,阶段 7)。
 *
 * 主键 = (mediaId, markNameNormalized):同一张照片同一字符串去重,
 * 但跨照片可以重用同一字符串,所以 markNameNormalized 单独建索引便于搜索。
 */
@Entity(
    tableName = "image_mark",
    primaryKeys = ["mediaId", "markNameNormalized"],
    indices = [Index("markNameNormalized")]
)
data class ImageMarkEntity(
    val mediaId: Long,
    val markNameOriginal: String,
    val markNameNormalized: String,
    val createdAt: Long
)
