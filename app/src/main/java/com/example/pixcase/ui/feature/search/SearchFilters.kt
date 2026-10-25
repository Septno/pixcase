package com.example.pixcase.ui.feature.search

import com.example.pixcase.data.model.groupingEpochSec
import com.example.pixcase.data.repository.FilterContext
import com.example.pixcase.data.repository.SearchedPhoto

/** 筛选面板的时间范围档位。 */
enum class SearchTimeRange { ALL, WEEK, MONTH }

/** 文件大小档位。 */
enum class SearchSizeBucket { ALL, SMALL, MEDIUM, LARGE }

/** 分辨率档位(按像素总量)。 */
enum class SearchDimensionBucket { ALL, SMALL, MEDIUM, LARGE }

/** MIME 档位。 */
enum class SearchMimeFilter { ALL, JPEG, PNG, OTHER }

/**
 * 搜索筛选(阶段 4 筛选面板):全部档位默认「不限」;
 * 收藏 / 隐藏 / 有标签为包含式开关;标签多选支持 AND / OR。
 */
data class SearchFilters(
    val timeRange: SearchTimeRange = SearchTimeRange.ALL,
    val sizeBucket: SearchSizeBucket = SearchSizeBucket.ALL,
    val dimensionBucket: SearchDimensionBucket = SearchDimensionBucket.ALL,
    val mime: SearchMimeFilter = SearchMimeFilter.ALL,
    val favoritesOnly: Boolean = false,
    val hiddenOnly: Boolean = false,
    val hasTag: Boolean = false,
    val tagIds: Set<Long> = emptySet(),
    /** true = 同时满足所选标签(AND);false = 任一满足(OR)。 */
    val tagMatchAll: Boolean = false
) {
    /** 激活中的筛选项数(过滤按钮角标)。 */
    val activeCount: Int
        get() = listOf(
            timeRange != SearchTimeRange.ALL,
            sizeBucket != SearchSizeBucket.ALL,
            dimensionBucket != SearchDimensionBucket.ALL,
            mime != SearchMimeFilter.ALL,
            favoritesOnly,
            hiddenOnly,
            hasTag,
            tagIds.isNotEmpty()
        ).count { it }

    val isActive: Boolean get() = activeCount > 0
}

private const val BYTES_PER_MB = 1L shl 20
private const val SECONDS_PER_DAY = 86_400L
private const val SMALL_PIXELS = 500_000
private const val LARGE_PIXELS = 2_000_000
private const val BYTES_PER_10MB = 10L * BYTES_PER_MB
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L
private val JPEG_MIME = setOf("image/jpeg", "image/jpg")
private const val PNG_MIME = "image/png"

/**
 * 对聚合结果应用筛选(纯函数便于单测):结果集有界(文件名 ≤200 + 成员 ≤500),
 * 内存过滤代价可控。默认排除隐藏照片(与时间线一致),勾选「仅隐藏」则反向只看隐藏;
 * 筛选与搜索词组合 = 在命中集合上叠加。
 */
internal fun filterResults(
    results: List<SearchedPhoto>,
    filters: SearchFilters,
    nowSec: Long,
    context: FilterContext
): List<SearchedPhoto> = results.filter { result ->
    val photoTags = context.tagsByMedia[result.photo.id].orEmpty()
    matchesTime(result, filters, nowSec) &&
        matchesSize(result, filters) &&
        matchesDimension(result, filters) &&
        matchesMime(result, filters) &&
        matchesFlags(result, filters, context) &&
        matchesTags(filters, photoTags)
}

private fun matchesTime(result: SearchedPhoto, filters: SearchFilters, nowSec: Long): Boolean {
    val epochSec = result.photo.groupingEpochSec()
    return when (filters.timeRange) {
        SearchTimeRange.ALL -> true
        SearchTimeRange.WEEK -> epochSec >= nowSec - DAYS_PER_WEEK * SECONDS_PER_DAY
        SearchTimeRange.MONTH -> epochSec >= nowSec - DAYS_PER_MONTH * SECONDS_PER_DAY
    }
}

private fun matchesSize(result: SearchedPhoto, filters: SearchFilters): Boolean {
    val bytes = result.photo.sizeBytes
    return when (filters.sizeBucket) {
        SearchSizeBucket.ALL -> true
        SearchSizeBucket.SMALL -> bytes in 1 until BYTES_PER_MB
        SearchSizeBucket.MEDIUM -> bytes in BYTES_PER_MB until BYTES_PER_10MB
        SearchSizeBucket.LARGE -> bytes >= BYTES_PER_10MB
    }
}

private fun matchesDimension(result: SearchedPhoto, filters: SearchFilters): Boolean {
    val pixels = result.photo.width.toLong() * result.photo.height.toLong()
    return when (filters.dimensionBucket) {
        SearchDimensionBucket.ALL -> true
        SearchDimensionBucket.SMALL -> pixels in 1 until SMALL_PIXELS
        SearchDimensionBucket.MEDIUM -> pixels in SMALL_PIXELS until LARGE_PIXELS
        SearchDimensionBucket.LARGE -> pixels >= LARGE_PIXELS
    }
}

private fun matchesMime(result: SearchedPhoto, filters: SearchFilters): Boolean {
    val mime = result.photo.mimeType.lowercase()
    return when (filters.mime) {
        SearchMimeFilter.ALL -> true
        SearchMimeFilter.JPEG -> mime in JPEG_MIME
        SearchMimeFilter.PNG -> mime == PNG_MIME
        SearchMimeFilter.OTHER -> mime !in JPEG_MIME && mime != PNG_MIME
    }
}

/** 收藏 / 隐藏 / 有标签:默认排除隐藏,勾选「仅隐藏」只看隐藏(与时间线口径一致)。 */
private fun matchesFlags(result: SearchedPhoto, filters: SearchFilters, context: FilterContext): Boolean {
    val id = result.photo.id
    val favoriteOk = !filters.favoritesOnly || id in context.favorites
    val hiddenOk = if (filters.hiddenOnly) id in context.hidden else id !in context.hidden
    val hasTagOk = !filters.hasTag || id in context.tagsByMedia
    return favoriteOk && hiddenOk && hasTagOk
}

/** 标签多选:AND = 所选全在照片标签集内,OR = 至少一个在。 */
private fun matchesTags(filters: SearchFilters, photoTags: Set<Long>): Boolean = when {
    filters.tagIds.isEmpty() -> true
    filters.tagMatchAll -> filters.tagIds.all { it in photoTags }
    else -> filters.tagIds.any { it in photoTags }
}
