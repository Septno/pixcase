package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 工具二(阶段 7)用的大类分类,与阶段 3 的 tag 表分开(详见计划 § 7.1)。 */
@Entity(
    tableName = "image_category",
    indices = [Index(value = ["name"], unique = true)]
)
data class ImageCategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val coverMediaId: Long? = null,
    val usageCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
)
