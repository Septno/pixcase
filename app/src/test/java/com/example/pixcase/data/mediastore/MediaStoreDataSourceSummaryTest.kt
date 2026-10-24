package com.example.pixcase.data.mediastore

import android.content.ContentResolver
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Looper
import android.provider.MediaStore
import app.cash.turbine.test
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
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
 * MediaStoreDataSource.observeSummary 的 JVM 覆盖:COUNT 聚合与最近一张两条查询的拼装、
 * ContentObserver 触发重查、值未变去重、取消即解注册。API 30+ 的 Bundle 取行路径依赖
 * android.os.Bundle,纯 JVM 无法构造,只能真机验证(useQueryArgs = false 覆盖 legacy 路径)。
 * ContentObserver 的主线程 Handler 依赖 Looper.getMainLooper(),JVM 里是静态桩,需 mock。
 */
class MediaStoreDataSourceSummaryTest {
    private val resolver = mockk<ContentResolver>()
    private val imagesUri = mockk<Uri>(relaxed = true)
    private val dataSource = MediaStoreDataSource(
        context = mockk { every { contentResolver } returns resolver },
        ioDispatcher = UnconfinedTestDispatcher(),
        imagesContentUri = imagesUri
    )

    @Before
    fun setup() {
        mockkStatic(Looper::class)
        every { Looper.getMainLooper() } returns mockk()
        every { resolver.registerContentObserver(any(), any(), capture(observerSlot)) } just runs
        every { resolver.unregisterContentObserver(any()) } just runs
    }

    @After
    fun teardown() {
        unmockkStatic(Looper::class)
    }

    private val observerSlot = slot<ContentObserver>()
    private val selectionSlot = slot<String>()
    private val selectionArgsSlot = slot<Array<String>>()
    private val latestSortOrder = slot<String>()

    private fun intCursor(value: Int): Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns true
        every { getInt(0) } returns value
    }

    private fun longCursor(value: Long): Cursor = mockk(relaxed = true) {
        every { moveToFirst() } returns true
        every { getLong(0) } returns value
    }

    /** 按投影列分流两条查询:COUNT 聚合 → 条数(捕获 selection),_ID → 最近一张。 */
    private fun stubQueries(count: Int, latestId: Long) {
        every {
            resolver.query(
                any(),
                match { it[0] == MediaStoreProjection.COUNT_AGGREGATE },
                capture(selectionSlot),
                capture(selectionArgsSlot),
                any()
            )
        } returns intCursor(count)
        every {
            resolver.query(
                any(),
                match { it[0] == MediaStore.Images.Media._ID },
                any(),
                any(),
                capture(latestSortOrder)
            )
        } returns longCursor(latestId)
    }

    @Test
    fun `observeSummary emits count and latest and re-queries on observer change`() = runTest {
        every { resolver.registerContentObserver(any(), any(), capture(observerSlot)) } just runs
        stubQueries(count = 7, latestId = 42L)

        dataSource.observeSummary(
            MediaStoreProjection.FAVORITE_SELECTION,
            emptyArray(),
            useQueryArgs = false
        ).test {
            assertEquals(MediaSummary(7, 42L), awaitItem())

            stubQueries(count = 9, latestId = 43L)
            observerSlot.captured.onChange(false)
            assertEquals(MediaSummary(9, 43L), awaitItem())

            // 概览值未变的 onChange 不重发(distinctUntilChanged)
            observerSlot.captured.onChange(false)
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }

        // selection / 最近一张的 LIMIT 1 都按 legacy 形态拼装
        assertEquals(MediaStoreProjection.FAVORITE_SELECTION, selectionSlot.captured)
        assertTrue(latestSortOrder.captured.contains("LIMIT 1 OFFSET 0"))
        verify(exactly = 1) { resolver.unregisterContentObserver(observerSlot.captured) }
    }

    @Test
    fun `observeSummary falls back to empty summary when queries return null`() = runTest {
        every { resolver.query(any(), any(), any(), any(), any()) } returns null

        dataSource.observeSummary("1=1", emptyArray(), useQueryArgs = false).test {
            assertEquals(MediaSummary(0, null), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
