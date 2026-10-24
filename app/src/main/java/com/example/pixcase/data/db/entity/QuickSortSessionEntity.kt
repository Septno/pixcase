package com.example.pixcase.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 快速整理会话(阶段 3.5)。
 * 每个相册同时只能有一个 active session,主键 = albumId 保证唯一性。
 * 目标相册列表在 [QuickSortSessionTargetEntity](陷阱 5),不在本表存字符串。
 */
@Entity(tableName = "quick_sort_session")
data class QuickSortSessionEntity(
    @PrimaryKey val albumId: Long,
    val startedAt: Long,
    val lastActiveAt: Long,
    val totalCount: Int,
    val processedCount: Int,
    val currentIndex: Int,
    val isCompleted: Boolean = false
)
