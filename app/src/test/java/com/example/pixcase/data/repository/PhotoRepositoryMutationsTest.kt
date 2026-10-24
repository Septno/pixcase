package com.example.pixcase.data.repository

import android.content.IntentSender
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.entity.FavoriteEntity
import com.example.pixcase.data.db.entity.HiddenEntity
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoRepository 写操作(阶段 2)的编排逻辑:
 * - Room 覆盖层是 UI 真源,先写;
 * - 删除完成后只清理**真正删掉**的条目的本地记录(陷阱 3:MediaStore 真删后留孤儿行);
 * - API 30+ 删除要等系统确认,同意前不动 Room。
 *
 * MediaStore 侧由 MediaStorePhotoWriter 承担并单独测试,这里全部 mock。
 */
class PhotoRepositoryMutationsTest {
    private val dataSource = mockk<MediaStoreDataSource>()
    private val writer = mockk<MediaStorePhotoWriter>()
    private val favoriteDao = mockk<FavoriteDao>()
    private val hiddenDao = mockk<HiddenDao>()
    private val repository = PhotoRepository(dataSource, writer, favoriteDao, hiddenDao)

    @Test
    fun `deletePhotos completed clears local metadata only for deleted ids`() = runTest {
        val outcome = MediaStorePhotoWriter.DeleteOutcome.Completed(1, listOf(2L))
        coEvery { writer.deleteImages(listOf(1L, 2L)) } returns outcome
        coEvery { favoriteDao.deleteAll(listOf(2L)) } just runs
        coEvery { hiddenDao.deleteAll(listOf(2L)) } just runs

        val result = repository.deletePhotos(listOf(1L, 2L))

        assertEquals(outcome, result)
        coVerify(exactly = 1) { favoriteDao.deleteAll(listOf(2L)) }
        coVerify(exactly = 1) { hiddenDao.deleteAll(listOf(2L)) }
    }

    @Test
    fun `deletePhotos needsConsent defers cleanup until user approval`() = runTest {
        val sender = mockk<IntentSender>()
        coEvery { writer.deleteImages(listOf(5L)) } returns MediaStorePhotoWriter.DeleteOutcome.NeedsConsent(sender)

        val result = repository.deletePhotos(listOf(5L))

        assertTrue(result is MediaStorePhotoWriter.DeleteOutcome.NeedsConsent)
        coVerify(exactly = 0) { favoriteDao.deleteAll(any()) }
        coVerify(exactly = 0) { hiddenDao.deleteAll(any()) }
    }

    @Test
    fun `onSystemDeleteApproved clears local metadata for the approved ids`() = runTest {
        coEvery { favoriteDao.deleteAll(setOf(9L)) } just runs
        coEvery { hiddenDao.deleteAll(setOf(9L)) } just runs

        repository.onSystemDeleteApproved(setOf(9L))

        coVerify(exactly = 1) { favoriteDao.deleteAll(setOf(9L)) }
        coVerify(exactly = 1) { hiddenDao.deleteAll(setOf(9L)) }
    }

    @Test
    fun `setFavorite inserts Room rows then asks writer to sync`() = runTest {
        val sender = mockk<IntentSender>()
        coEvery { writer.syncFavorite(listOf(7L, 8L), true) } returns sender
        coEvery { favoriteDao.insertAll(any()) } just runs

        val returned = repository.setFavorite(listOf(7L, 8L), true)

        assertEquals(sender, returned)
        coVerify {
            favoriteDao.insertAll(
                match<List<FavoriteEntity>> { entities ->
                    entities.map { it.mediaId } == listOf(7L, 8L)
                }
            )
        }
    }

    @Test
    fun `setFavorite unfavorite deletes Room rows`() = runTest {
        coEvery { writer.syncFavorite(listOf(7L), false) } returns null
        coEvery { favoriteDao.deleteAll(listOf(7L)) } just runs

        assertNull(repository.setFavorite(listOf(7L), false))

        coVerify(exactly = 1) { favoriteDao.deleteAll(listOf(7L)) }
        coVerify(exactly = 0) { favoriteDao.insertAll(any()) }
    }

    @Test
    fun `setFavorite with empty ids is a no-op`() = runTest {
        assertNull(repository.setFavorite(emptyList(), true))
        coVerify(exactly = 0) { writer.syncFavorite(any(), any()) }
        coVerify(exactly = 0) { favoriteDao.insertAll(any()) }
        coVerify(exactly = 0) { favoriteDao.deleteAll(any()) }
    }

    @Test
    fun `hidePhotos inserts hidden rows and never touches favorite`() = runTest {
        coEvery { hiddenDao.insertAll(any()) } just runs

        repository.hidePhotos(listOf(3L, 4L))

        coVerify {
            hiddenDao.insertAll(
                match<List<HiddenEntity>> { entities ->
                    entities.map { it.mediaId } == listOf(3L, 4L)
                }
            )
        }
        coVerify(exactly = 0) { favoriteDao.insertAll(any()) }
    }

    @Test
    fun `favoritesOf with empty ids returns empty set without dao call`() = runTest {
        assertTrue(repository.favoritesOf(emptyList()).isEmpty())
        coVerify(exactly = 0) { favoriteDao.favoritesOf(any()) }
    }
}
