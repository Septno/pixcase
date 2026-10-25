package com.example.pixcase.ui.feature.search

import android.net.Uri
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.FilterContext
import com.example.pixcase.data.repository.SearchedPhoto
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 筛选纯函数 filterResults 的覆盖:各维度独立生效、组合生效、
 * 默认排除隐藏、「仅隐藏」反向、标签 AND/OR、边界档位。
 */
class SearchFiltersTest {
    @Suppress("LongParameterList") // 测试工厂:六个字段各对应一个筛选维度,默认值覆盖大部分用例
    private fun photo(
        id: Long,
        addedSec: Long = 10_000L,
        sizeBytes: Long = 0L,
        width: Int = 0,
        height: Int = 0,
        mime: String = "image/jpeg"
    ) = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "img_$id.jpg",
        mimeType = mime,
        dateAddedSec = addedSec,
        width = width,
        height = height,
        sizeBytes = sizeBytes
    )

    private fun results(vararg photos: MediaPhoto) = photos.map { SearchedPhoto(it, emptySet()) }

    private val now = 1_000_000L
    private val context = FilterContext(
        favorites = setOf(1L),
        hidden = setOf(2L),
        tagsByMedia = mapOf(1L to setOf(10L, 11L), 3L to setOf(11L))
    )

    private fun apply(filters: SearchFilters, photos: List<MediaPhoto>): List<SearchedPhoto> =
        filterResults(results(*photos.toTypedArray()), filters, now, context)

    @Test
    fun `no filters keep everything except hidden by default`() {
        val ids = apply(SearchFilters(), listOf(photo(1L), photo(2L), photo(3L)))
        // 隐藏照片默认排除(与时间线一致)
        assertEquals(listOf(1L, 3L), ids.map { it.photo.id })
    }

    @Test
    fun `hidden only inverts to show only hidden`() {
        val ids = apply(SearchFilters(hiddenOnly = true), listOf(photo(1L), photo(2L)))
        assertEquals(listOf(2L), ids.map { it.photo.id })
    }

    @Test
    fun `week range keeps only recent photos`() {
        val recent = photo(1L, addedSec = now - 86_400L)
        val old = photo(3L, addedSec = now - 30 * 86_400L)
        val ids = apply(SearchFilters(timeRange = SearchTimeRange.WEEK), listOf(recent, old))
        assertEquals(listOf(1L), ids.map { it.photo.id })
    }

    @Test
    fun `size buckets partition by bytes`() {
        val small = photo(1L, sizeBytes = 500L * 1_024)
        val medium = photo(3L, sizeBytes = 5L * 1_024 * 1_024)
        val large = photo(4L, sizeBytes = 20L * 1_024 * 1_024)

        assertEquals(
            listOf(4L),
            apply(SearchFilters(sizeBucket = SearchSizeBucket.LARGE), listOf(small, medium, large))
                .map { it.photo.id }
        )
        assertEquals(
            listOf(3L),
            apply(SearchFilters(sizeBucket = SearchSizeBucket.MEDIUM), listOf(small, medium, large))
                .map { it.photo.id }
        )
    }

    @Test
    fun `dimension buckets partition by pixel count`() {
        val small = photo(1L, width = 600, height = 600)
        val medium = photo(3L, width = 1200, height = 800)
        val large = photo(4L, width = 2500, height = 1500)

        assertEquals(
            listOf(4L),
            apply(SearchFilters(dimensionBucket = SearchDimensionBucket.LARGE), listOf(small, medium, large))
                .map { it.photo.id }
        )
    }

    @Test
    fun `mime filter matches case insensitively`() {
        val jpeg = photo(1L, mime = "IMAGE/JPEG")
        val png = photo(3L, mime = "image/png")
        val gif = photo(4L, mime = "image/gif")

        assertEquals(
            listOf(3L),
            apply(SearchFilters(mime = SearchMimeFilter.PNG), listOf(jpeg, png, gif)).map { it.photo.id }
        )
        assertEquals(
            listOf(4L),
            apply(SearchFilters(mime = SearchMimeFilter.OTHER), listOf(jpeg, png, gif)).map { it.photo.id }
        )
    }

    @Test
    fun `favorites only keeps favorites`() {
        val ids = apply(SearchFilters(favoritesOnly = true), listOf(photo(1L), photo(3L)))
        assertEquals(listOf(1L), ids.map { it.photo.id })
    }

    @Test
    fun `has tag keeps only tagged photos`() {
        val ids = apply(SearchFilters(hasTag = true), listOf(photo(1L), photo(4L)))
        assertEquals(listOf(1L), ids.map { it.photo.id })
    }

    @Test
    fun `tag match all requires every selected tag`() {
        val tags10And11 = photo(1L) // 标签 {10, 11}
        val tag11Only = photo(3L) // 标签 {11}

        val andIds = apply(
            SearchFilters(tagIds = setOf(10L, 11L), tagMatchAll = true),
            listOf(tags10And11, tag11Only)
        )
        assertEquals(listOf(1L), andIds.map { it.photo.id })

        val orIds = apply(
            SearchFilters(tagIds = setOf(10L, 11L), tagMatchAll = false),
            listOf(tags10And11, tag11Only)
        )
        assertEquals(listOf(1L, 3L), orIds.map { it.photo.id })
    }

    @Test
    fun `active count reflects each dimension`() {
        assertEquals(0, SearchFilters().activeCount)
        assertEquals(
            4,
            SearchFilters(
                timeRange = SearchTimeRange.WEEK,
                mime = SearchMimeFilter.PNG,
                favoritesOnly = true,
                tagIds = setOf(1L)
            ).activeCount
        )
    }
}
