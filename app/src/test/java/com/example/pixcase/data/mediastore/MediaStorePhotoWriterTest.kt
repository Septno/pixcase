package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * MediaStorePhotoWriter 的 JVM 覆盖。
 *
 * 纯 JVM 单测里 Build.VERSION.SDK_INT = 0,deleteImages 走 API 29 及以下的直接删除路径、
 * syncFavorite 走「无 IS_FAVORITE 列,纯 no-op」分支;API 30+ 的 createDeleteRequest /
 * createFavoriteRequest 依赖真机 MediaProvider,无法在此覆盖,只能真机验证。
 */
class MediaStorePhotoWriterTest {
    private val resolver = mockk<ContentResolver>()
    private val imagesUri = mockk<Uri>(relaxed = true)
    private val writer = MediaStorePhotoWriter(
        context = mockk { every { contentResolver } returns resolver },
        ioDispatcher = UnconfinedTestDispatcher(),
        contentUri = imagesUri
    )

    @Before
    fun setup() {
        mockkStatic(ContentUris::class)
        every { ContentUris.withAppendedId(any(), any()) } answers { mockk(relaxed = true) }
    }

    @After
    fun teardown() {
        unmockkStatic(ContentUris::class)
    }

    @Test
    fun `deleteImages with empty ids completes without touching resolver`() = runTest {
        val outcome = writer.deleteImages(emptyList())

        assertEquals(MediaStorePhotoWriter.DeleteOutcome.Completed(0, emptyList()), outcome)
        verify(exactly = 0) { resolver.delete(any(), any(), any()) }
    }

    @Test
    fun `deleteImages legacy path deletes each image and reports deleted ids`() = runTest {
        every { resolver.delete(any(), any(), any()) } returns 1

        val outcome = writer.deleteImages(listOf(1L, 2L)) as MediaStorePhotoWriter.DeleteOutcome.Completed

        assertEquals(2, outcome.deletedCount)
        assertEquals(listOf(1L, 2L), outcome.deletedIds)
        verify(exactly = 2) { ContentUris.withAppendedId(imagesUri, any()) }
    }

    @Test
    fun `deleteImages skips rows the system refuses and keeps the rest`() = runTest {
        var call = 0
        every { resolver.delete(any(), any(), any()) } answers {
            call++
            if (call == 1) throw SecurityException("denied") else 1
        }

        val outcome = writer.deleteImages(listOf(1L, 2L)) as MediaStorePhotoWriter.DeleteOutcome.Completed

        assertEquals(1, outcome.deletedCount)
        assertEquals(listOf(2L), outcome.deletedIds)
    }

    @Test
    fun `syncFavorite without IS_FAVORITE column support is a no-op`() = runTest {
        assertNull(writer.syncFavorite(listOf(1L), true))
        verify(exactly = 0) { resolver.update(any(), any(), any(), any()) }
    }

    @Test
    fun `syncFavorite with empty ids returns null`() = runTest {
        assertNull(writer.syncFavorite(emptyList(), false))
    }
}
