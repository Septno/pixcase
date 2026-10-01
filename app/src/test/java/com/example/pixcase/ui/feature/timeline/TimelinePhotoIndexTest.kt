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
}
