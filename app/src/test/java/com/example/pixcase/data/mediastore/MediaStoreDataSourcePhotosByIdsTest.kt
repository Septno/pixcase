package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MediaStoreDataSource.photosByIds 的 JVM 覆盖:IN 查询的 selection 拼装、行映射循环、
 * 空集合短路。images Uri 经 @ImagesContentUri 注入,保证 JVM 可构造(同 Writer 模式)。
 */
class MediaStoreDataSourcePhotosByIdsTest {
    private val resolver = mockk<ContentResolver>()
    private val imagesUri = mockk<Uri>(relaxed = true)
    private val appendedUri = mockk<Uri>(relaxed = true)
    private val dataSource = MediaStoreDataSource(
        context = mockk { every { contentResolver } returns resolver },
        ioDispatcher = UnconfinedTestDispatcher(),
        imagesContentUri = imagesUri
    )

    @Before
    fun setup() {
        mockkStatic(ContentUris::class)
        every { ContentUris.withAppendedId(any(), any<Long>()) } returns appendedUri
    }

    @After
    fun teardown() {
        unmockkStatic(ContentUris::class)
    }

    /** 两行 cursor:id / displayName 随 moveToNext 推进变化,验证映射循环走满两行。 */
    private fun twoRowCursor(): Cursor {
        val cursor = mockk<Cursor>(relaxed = true)
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID) } returns 0
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME) } returns 1
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED) } returns 2
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME) } returns 3
        every { cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE) } returns 4
        every { cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN) } returns 5

        // relaxed mock 的 position 不会自己推进,用计数器模拟真实游标的行进语义
        var row = -1
        every { cursor.moveToNext() } answers {
            row++
            row < 2
        }
        every { cursor.isNull(any()) } returns true
        every { cursor.getLong(0) } answers { if (row == 0) 5L else 9L }
        every { cursor.getLong(2) } returns 1_700_000_000L
        every { cursor.isNull(1) } returns false
        every { cursor.getString(1) } answers { "img_${if (row == 0) 5 else 9}.jpg" }
        return cursor
    }

    @Test
    fun `photosByIds queries with IN placeholders and maps rows`() = runTest {
        val selection = slot<String>()
        val selectionArgs = slot<Array<String>>()
        every {
            resolver.query(any(), any(), capture(selection), capture(selectionArgs), any())
        } returns twoRowCursor()

        val photos = dataSource.photosByIds(listOf(5L, 9L))

        assertEquals(listOf(5L, 9L), photos.map { it.id })
        assertEquals("_id IN (?,?)", selection.captured)
        assertEquals(listOf("5", "9"), selectionArgs.captured.toList())
    }

    @Test
    fun `photosByIds with empty ids short-circuits without touching resolver`() = runTest {
        assertTrue(dataSource.photosByIds(emptyList()).isEmpty())
        verify(exactly = 0) { resolver.query(any(), any(), any(), any(), any()) }
    }
}
