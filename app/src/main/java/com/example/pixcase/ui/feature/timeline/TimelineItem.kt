package com.example.pixcase.ui.feature.timeline

import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.model.groupingEpochSec
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 时间线列表项:照片与日期表头共用同一类型。
 *
 * 日期表头由 `insertSeparators` 从相邻照片推导,插进列表后就是一等 item,
 * 因此 LazyGrid 的 key / contentType / span 对每个下标都能直接确定,
 * 不需要在下标取值时反查「这个位置是照片还是表头」。
 */
sealed interface TimelineItem {

    /** LazyGrid 的 key。跨 ContentObserver 失效刷新保持稳定,决定条目与 Composable 的复用。 */
    val cacheKey: Any

    /** LazyGrid 的 contentType,决定 Composable 复用池;照片按 MIME 类型分池。 */
    val contentType: String

    data class Photo(val photo: MediaPhoto) : TimelineItem {
        override val cacheKey: Any get() = photo.id
        override val contentType: String get() = photo.mimeType
    }

    data class DateHeader(val date: LocalDate) : TimelineItem {
        override val cacheKey: Any get() = "$KEY_PREFIX${date.toEpochDay()}"
        override val contentType: String get() = CONTENT_TYPE

        companion object {
            const val KEY_PREFIX = "date-header-"
            const val CONTENT_TYPE = "date-header"
        }
    }
}

/**
 * 判断两个相邻照片之间是否需要插入日期表头。
 *
 * `insertSeparators` 在列表两端会传 null:[before] 为 null 表示这是列表首项(必须产出表头),
 * [after] 为 null 表示已经到列表末尾(不产出)。
 *
 * 日期按 [zone] 换算日历日 —— 同一个 epoch 秒在两个时区可能落在不同日期,分组必须以本地日历为准。
 */
internal fun headerBetween(before: MediaPhoto?, after: MediaPhoto?, zone: ZoneId): TimelineItem.DateHeader? {
    if (after == null) return null
    val afterDate = after.localDateIn(zone)
    val sameDayAsBefore = before != null && before.localDateIn(zone) == afterDate
    return if (sameDayAsBefore) null else TimelineItem.DateHeader(afterDate)
}

private fun MediaPhoto.localDateIn(zone: ZoneId): LocalDate =
    Instant.ofEpochSecond(groupingEpochSec()).atZone(zone).toLocalDate()
