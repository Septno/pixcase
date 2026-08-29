package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 隐藏(仅在本 App 内不显示,原图仍在系统相册)。 */
@Entity(tableName = "hidden_photo")
data class HiddenEntity(
    @PrimaryKey val mediaId: Long,
    val addedAt: Long
)
