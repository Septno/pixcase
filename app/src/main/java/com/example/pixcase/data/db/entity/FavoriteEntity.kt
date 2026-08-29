package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 收藏(以 mediaId 为主键,本表只记录"被收藏"集合)。 */
@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey val mediaId: Long,
    val addedAt: Long
)
