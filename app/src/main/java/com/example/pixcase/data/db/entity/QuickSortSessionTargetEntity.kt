package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 快速整理会话的目标相册关联(陷阱 5 解法):独立成表取代逗号分隔字符串,
 * position 承担 Dock 显示顺序,JOIN 与删除都走索引。
 */
@Entity(
    tableName = "quick_sort_session_target",
    primaryKeys = ["sessionAlbumId", "position"],
    indices = [Index("targetAlbumId")]
)
data class QuickSortSessionTargetEntity(
    val sessionAlbumId: Long,
    val position: Int,
    val targetAlbumId: Long
)
