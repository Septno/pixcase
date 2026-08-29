package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/** 标签 - 照片多对多。 */
@Entity(
    tableName = "photo_tag",
    primaryKeys = ["tagId", "mediaId"],
    indices = [Index("mediaId")]
)
data class PhotoTagCrossRef(
    val tagId: Long,
    val mediaId: Long
)
