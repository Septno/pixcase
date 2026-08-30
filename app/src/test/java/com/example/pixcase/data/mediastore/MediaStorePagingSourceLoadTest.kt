package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import androidx.paging.PagingSource.LoadParams
import androidx.paging.PagingSource.LoadResult
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStorePagingSourceLoadTest {
    private val resolver = mockk<ContentResolver>()
    private val uri: Uri = mockk(relaxed = true)

    private fun emptyCursor(): Cursor = mockk(relaxed = true) {
        every { moveToNext() } returns false
    }

    @Test
    fun `load with key 0 computes sort order with offset 0`() = runTest {
        val sortOrder = slot<String>()
        every { resolver.query(any(), any(), any(), any(), capture(sortOrder)) } returns emptyCursor()
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        source.load(LoadParams.Refresh(key = 0, loadSize = 100, placeholdersEnabled = false))

        assertTrue(
            "expected LIMIT 100 OFFSET 0, got ${sortOrder.captured}",
            sortOrder.captured.contains("LIMIT 100 OFFSET 0")
        )
    }

    @Test
    fun `load with key 3 computes sort order with offset 300`() = runTest {
        val sortOrder = slot<String>()
        every { resolver.query(any(), any(), any(), any(), capture(sortOrder)) } returns emptyCursor()
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        source.load(LoadParams.Refresh(key = 3, loadSize = 100, placeholdersEnabled = false))

        assertTrue(
            "expected LIMIT 100 OFFSET 300, got ${sortOrder.captured}",
            sortOrder.captured.contains("LIMIT 100 OFFSET 300")
        )
    }

    @Test
    fun `load returns Page with prevKey null when key is 0`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns emptyCursor()
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        val result = source.load(LoadParams.Refresh(key = 0, loadSize = 100, placeholdersEnabled = false))

        assertTrue(result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertNull(page.prevKey)
        // 空 cursor → items.size(0) != loadSize(100),nextKey null
        assertNull(page.nextKey)
    }

    @Test
    fun `load returns Page with prevKey page minus 1 when key greater than 0`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns emptyCursor()
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        val result = source.load(LoadParams.Refresh(key = 2, loadSize = 100, placeholdersEnabled = false))

        assertTrue(result is LoadResult.Page)
        val page = result as LoadResult.Page
        assertEquals(1, page.prevKey)
    }

    @Test
    fun `load returns LoadResult Error when resolver throws`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } throws
            SecurityException("READ_MEDIA_IMAGES not granted")
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        val result = source.load(LoadParams.Refresh(key = 0, loadSize = 100, placeholdersEnabled = false))

        assertTrue(result is LoadResult.Error)
    }

    @Test
    fun `load clamps loadSize to MAX_LOAD_SIZE`() = runTest {
        val sortOrder = slot<String>()
        every { resolver.query(any(), any(), any(), any(), capture(sortOrder)) } returns emptyCursor()
        val source = MediaStorePagingSource(resolver, uri, MediaStoreProjection.IMAGE_COLUMNS)

        source.load(LoadParams.Refresh(key = 0, loadSize = 100_000, placeholdersEnabled = false))

        assertTrue(
            "expected LIMIT ${MediaStorePagingSource.MAX_LOAD_SIZE} OFFSET 0, got ${sortOrder.captured}",
            sortOrder.captured.contains("LIMIT ${MediaStorePagingSource.MAX_LOAD_SIZE} OFFSET 0")
        )
    }
}
