package com.example.pixcase.ui.feature.timeline

import com.example.pixcase.data.model.MediaPhoto

/**
 * 从混有日期表头的列表里取出照片项,保持原顺序。
 *
 * 查看器按「第几张照片」翻页,而 `LazyPagingItems` 的下标把日期表头也算在内 ——
 * 两套下标必须分开,否则点第 3 张照片会翻到第 3 个列表项(可能是表头)。
 */
internal fun photosIn(items: List<TimelineItem>): List<MediaPhoto> =
    items.mapNotNull { (it as? TimelineItem.Photo)?.photo }
