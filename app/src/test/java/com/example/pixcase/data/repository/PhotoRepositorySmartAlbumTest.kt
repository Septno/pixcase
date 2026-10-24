package com.example.pixcase.data.repository

import android.net.Uri
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.mediastore.MediaStoreProjection
import com.example.pixcase.data.mediastore.MediaSummary
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoRepository 智能相册分流的 JVM 覆盖:
 * - 收藏按 API 分流:30+ 走 MediaStore IS_FAVORITE 条件分页,更低版本回退 Room 覆盖层;
 * - 截图恒走 RELATIVE_PATH 条件分页;
 * - Room 回退路径的 wiring(observeAllIds → photosByIds),顺序语义由 [orderByIds] 纯函数
 *   覆盖(PagingData 内容在 JVM 不可 inspect,与 PhotoRepositoryTest 同约定)。
 */
class PhotoRepositorySmartAlbumTest {
    private val dataSource = mockk<MediaStoreDataSource>(relaxed = true)
    private val writer = mockk<MediaStorePhotoWriter>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>()
    private val hiddenDao = mockk<HiddenDao>(relaxed = true)
    private val albumPhotoDao = mockk<AlbumPhotoDao>(relaxed = true)
    private val photoTagDao = mockk<PhotoTagDao>(relaxed = true)
    private val repository = PhotoRepository(dataSource, writer, favoriteDao, hiddenDao, albumPhotoDao, photoTagDao)

    private fun photo(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "img_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    /** Paging 3 没有 JVM 可用的静态 PagingSource 工厂,测试里给个返回空页的假源。 */
    private fun emptyPagingSource(): PagingSource<Int, MediaPhoto> = object : PagingSource<Int, MediaPhoto>() {
        override suspend fun load(params: PagingSource.LoadParams<Int>): PagingSource.LoadResult<Int, MediaPhoto> =
            PagingSource.LoadResult.Page(data = emptyList(), prevKey = null, nextKey = null)

        override fun getRefreshKey(state: PagingState<Int, MediaPhoto>): Int? = null
    }

    @Test
    fun `orderByIds keeps overlay order and drops missing photos`() {
        val ordered = orderByIds(
            ids = listOf(3L, 1L, 7L),
            photos = listOf(photo(1L), photo(7L), photo(3L))
        )

        assertEquals(listOf(3L, 1L, 7L), ordered.map { it.id })
    }

    @Test
    fun `orderByIds skips ids absent from MediaStore`() {
        val ordered = orderByIds(
            ids = listOf(3L, 1L),
            photos = listOf(photo(1L))
        )

        assertEquals(listOf(1L), ordered.map { it.id })
    }

    @Test
    fun `smartFavorites below API 30 falls back to Room overlay`() = runTest {
        every { favoriteDao.observeAllIds() } returns flowOf(listOf(3L, 1L))
        coEvery { dataSource.photosByIds(listOf(3L, 1L)) } returns listOf(photo(1L), photo(3L))

        val emissions = repository.smartFavorites(sdkInt = 28).take(1).toList()

        assertEquals(1, emissions.size)
        coVerify(exactly = 1) { dataSource.photosByIds(listOf(3L, 1L)) }
        verify(exactly = 0) { dataSource.imagesPagingSource(any(), any()) }
    }

    @Test
    fun `smartFavorites on API 30 plus queries favorite selection`() = runTest {
        val selection = slot<String>()
        val selectionArgs = slot<Array<String>>()
        every {
            dataSource.imagesPagingSource(capture(selection), capture(selectionArgs))
        } returns emptyPagingSource()

        val emissions = repository.smartFavorites(sdkInt = 30).take(1).toList()

        assertEquals(1, emissions.size)
        assertEquals(MediaStoreProjection.FAVORITE_SELECTION, selection.captured)
        assertTrue(selectionArgs.captured.isEmpty())
    }

    @Test
    fun `smartScreenshots queries screenshots selection`() = runTest {
        val selection = slot<String>()
        every {
            dataSource.imagesPagingSource(capture(selection), any())
        } returns emptyPagingSource()

        val emissions = repository.smartScreenshots().take(1).toList()

        assertEquals(1, emissions.size)
        assertEquals(MediaStoreProjection.SCREENSHOTS_SELECTION, selection.captured)
    }

    @Test
    fun `favorites summary on API 30 plus observes MediaStore summary`() = runTest {
        val selection = slot<String>()
        every { dataSource.observeSummary(capture(selection), any(), any()) } returns
            flowOf(MediaSummary(count = 5, latestMediaId = 9L))

        val summary = repository.observeFavoritesSummary(sdkInt = 30).first()

        assertEquals(MediaSummary(5, 9L), summary)
        assertEquals(MediaStoreProjection.FAVORITE_SELECTION, selection.captured)
    }

    @Test
    fun `favorites summary below API 30 uses Room overlay latest favorite`() = runTest {
        every { favoriteDao.observeAllIds() } returns flowOf(listOf(3L, 1L))

        val summary = repository.observeFavoritesSummary(sdkInt = 28).first()

        // observeAllIds 按收藏时间倒序:首条即最近收藏,作为封面
        assertEquals(MediaSummary(count = 2, latestMediaId = 3L), summary)
    }

    @Test
    fun `screenshots summary observes MediaStore screenshots selection`() = runTest {
        val selection = slot<String>()
        every { dataSource.observeSummary(capture(selection), any(), any()) } returns
            flowOf(MediaSummary(count = 0, latestMediaId = null))

        val summary = repository.observeScreenshotsSummary().first()

        assertEquals(MediaSummary(0, null), summary)
        assertEquals(MediaStoreProjection.SCREENSHOTS_SELECTION, selection.captured)
    }
}
