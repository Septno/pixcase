package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户自定义相册 / 智能相册。 */
@Entity(tableName = "album")
data class AlbumEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverMediaId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val isSystem: Boolean = false,
    val systemType: String? = null
)