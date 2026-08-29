package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 快速整理每次决策的记录。
 * 同一张照片在同一 session 只能有一条记录(SKIP 也算"已处理")。
 */
@Entity(
    tableName = "quick_sort_decision",
    primaryKeys = ["sessionAlbumId", "mediaId"],
    indices = [Index("mediaId")]
)
data class QuickSortDecisionEntity(
    val sessionAlbumId: Long,
    val mediaId: Long,
    /** KEEP / SKIP / TRASH / MOVE_TO_<albumId> */
    val decision: String,
    val decidedAt: Long
)
