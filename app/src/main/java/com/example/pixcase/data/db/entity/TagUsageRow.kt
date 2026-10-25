package com.example.pixcase.data.db.entity

/** 标签使用量聚合行(photo_tag GROUP BY tagId),搜索建议按用量排序用。 */
data class TagUsageRow(
    val tagId: Long,
    val usageCount: Int
)
