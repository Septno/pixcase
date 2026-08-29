package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 分类 - 照片多对多。 */
@Entity(
    tableName = "image_category_cross",
    primaryKeys = ["categoryId", "mediaId"],
    indices = [Index("mediaId")]
)
data class ImageCategoryCrossRef(
    val categoryId: Long,
    val mediaId: Long,
    val addedAt: Long
)