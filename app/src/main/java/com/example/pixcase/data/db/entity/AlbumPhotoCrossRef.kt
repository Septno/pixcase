package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 相册 - 照片多对多关联。 */
@Entity(
    tableName = "album_photo",
    primaryKeys = ["albumId", "mediaId"],
    indices = [Index("mediaId")]
)
data class AlbumPhotoCrossRef(
    val albumId: Long,
    val mediaId: Long,
    val addedAt: Long
)
