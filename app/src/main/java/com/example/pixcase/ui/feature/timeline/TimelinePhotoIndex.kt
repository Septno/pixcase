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

/** 快照里的一张照片 + 它在分页列表里的下标(含表头计数,见 [photosIn] 的两套下标注释)。 */
internal data class IndexedPhoto(val pagingIndex: Int, val photo: MediaPhoto)

/**
 * [photosIn] 的带分页下标版本:查看器翻页时按 [IndexedPhoto.pagingIndex] 经
 * `LazyPagingItems.get()` 回读,产生 access hint 驱动预取(读快照列表不产生提示,
 * 计划 § 六 1.3 记录的窗口冻结根因)。
 */
internal fun photosIndexedIn(items: List<TimelineItem>): List<IndexedPhoto> = items.mapIndexedNotNull { index, item ->
    (item as? TimelineItem.Photo)?.photo?.let { IndexedPhoto(index, it) }
}
