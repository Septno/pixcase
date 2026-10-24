package com.example.pixcase.ui.feature.timeline

import android.net.Uri
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.mockk
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TimelinePhotoIndexTest {
    private fun photo(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    private fun header(day: Int): TimelineItem.DateHeader = TimelineItem.DateHeader(LocalDate.of(2026, 5, day))

    @Test
    fun `keeps only photos in original order`() {
        val items = listOf(
            header(2),
            TimelineItem.Photo(photo(1L)),
            TimelineItem.Photo(photo(2L)),
            header(1),
            TimelineItem.Photo(photo(3L))
        )

        assertEquals(listOf(1L, 2L, 3L), photosIn(items).map { it.id })
    }

    @Test
    fun `headers alone yield no photos`() {
        assertEquals(emptyList<MediaPhoto>(), photosIn(listOf(header(1), header(2))))
    }

    @Test
    fun `empty list stays empty`() {
        assertEquals(emptyList<MediaPhoto>(), photosIn(emptyList()))
    }

    @Test
    fun `photo index ignores headers`() {
        // 目标在列表里是第 4 个元素,但在照片里是第 2 张 —— 查看器的分页下标必须用后者。
        val items = listOf(header(2), TimelineItem.Photo(photo(1L)), header(1), TimelineItem.Photo(photo(2L)))

        assertEquals(1, photosIn(items).indexOfFirst { it.id == 2L })
    }

    @Test
    fun `indexed photos keep order and carry paging index counting headers`() {
        // 分页下标(查看器经 LazyPagingItems.get() 回读用)与照片序号是两套:表头占位计入前者。
        val items = listOf(
            header(2),
            TimelineItem.Photo(photo(1L)),
            TimelineItem.Photo(photo(2L)),
            header(1),
            TimelineItem.Photo(photo(3L))
        )

        val indexed = photosIndexedIn(items)

        assertEquals(listOf(1L, 2L, 3L), indexed.map { it.photo.id })
        assertEquals(listOf(1, 2, 4), indexed.map { it.pagingIndex })
    }

    @Test
    fun `indexed photos alone yield nothing`() {
        assertEquals(emptyList<IndexedPhoto>(), photosIndexedIn(listOf(header(1), header(2))))
    }
}
