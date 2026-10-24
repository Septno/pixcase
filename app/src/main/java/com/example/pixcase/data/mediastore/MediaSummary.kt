package com.example.pixcase.data.mediastore

/**
 * MediaStore 条件筛选的概览:命中条数与最近一张的 mediaId,支撑智能相册卡片的计数与封面。
 * latestMediaId 为 null 表示筛选结果为空。
 */
data class MediaSummary(
    val count: Int,
    val latestMediaId: Long?
)
