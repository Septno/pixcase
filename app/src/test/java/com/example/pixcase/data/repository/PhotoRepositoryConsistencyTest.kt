package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.entity.FavoriteEntity
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoRepository 数据一致性兜底的覆盖:
 * - 孤儿清扫:只清 MediaStore 已删的 id、四张关联表一次扫净、幂等(表干净时不产生删除)、
 *   大批量分块(SQLite 绑定变量上限);
 * - 收藏反向同步:宽限期内的新收藏不被误清(与 createFavoriteRequest 生效延迟的竞态防护)、
 *   API 29 及以下(无 IS_FAVORITE 列)整体短路。
 * MediaStoreSyncWorker 只是调度壳,不在 JVM 测试面。
 */
class PhotoRepositoryConsistencyTest {
    private val dataSource = mockk<MediaStoreDataSource>()
    private val writer = mockk<MediaStorePhotoWriter>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>()
    private val hiddenDao = mockk<HiddenDao>()
    private val albumPhotoDao = mockk<AlbumPhotoDao>()
    private val photoTagDao = mockk<PhotoTagDao>()
    private val repository = PhotoRepository(dataSource, writer, favoriteDao, hiddenDao, albumPhotoDao, photoTagDao)

    private fun favorite(mediaId: Long, addedAt: Long) = FavoriteEntity(mediaId, addedAt)

    private fun stubDeletes() {
        coEvery { favoriteDao.deleteAll(any<Collection<Long>>()) } returns Unit
        coEvery { hiddenDao.deleteAll(any<Collection<Long>>()) } returns Unit
        coEvery { albumPhotoDao.deleteByMediaIds(any()) } returns Unit
        coEvery { photoTagDao.deleteByMediaIds(any()) } returns Unit
    }

    @Test
    fun `sweepOrphans deletes only ids missing from MediaStore across tables`() = runTest {
        coEvery { dataSource.queryAllImageIds() } returns setOf(1L, 2L)
        coEvery { favoriteDao.getAll() } returns listOf(favorite(1L, 0L), favorite(3L, 0L))
        coEvery { hiddenDao.allMediaIds() } returns listOf(3L)
        coEvery { albumPhotoDao.allMediaIds() } returns listOf(2L, 3L)
        coEvery { photoTagDao.allMediaIds() } returns emptyList()
        stubDeletes()

        val removed = repository.sweepOrphans()

        // 只有 3 是孤儿:1 在收藏表但仍在 MediaStore,2 在相册关联但仍在 MediaStore
        assertEquals(1, removed)
        coVerify(exactly = 1) { favoriteDao.deleteAll(listOf(3L)) }
        coVerify(exactly = 1) { hiddenDao.deleteAll(listOf(3L)) }
        coVerify(exactly = 1) { albumPhotoDao.deleteByMediaIds(listOf(3L)) }
        coVerify(exactly = 1) { photoTagDao.deleteByMediaIds(listOf(3L)) }
    }

    @Test
    fun `sweepOrphans produces no deletes when tables are clean`() = runTest {
        coEvery { dataSource.queryAllImageIds() } returns setOf(1L)
        coEvery { favoriteDao.getAll() } returns listOf(favorite(1L, 0L))
        coEvery { hiddenDao.allMediaIds() } returns listOf(1L)
        coEvery { albumPhotoDao.allMediaIds() } returns listOf(1L)
        coEvery { photoTagDao.allMediaIds() } returns listOf(1L)
        stubDeletes()

        val removed = repository.sweepOrphans()

        assertEquals(0, removed)
        coVerify(exactly = 0) { favoriteDao.deleteAll(any<Collection<Long>>()) }
        coVerify(exactly = 0) { hiddenDao.deleteAll(any<Collection<Long>>()) }
        coVerify(exactly = 0) { albumPhotoDao.deleteByMediaIds(any()) }
        coVerify(exactly = 0) { photoTagDao.deleteByMediaIds(any()) }
    }

    @Test
    fun `sweepOrphans chunks large orphan batches`() = runTest {
        // 1000 个孤儿(id 2..1001)→ 按上限 500 分两块
        coEvery { dataSource.queryAllImageIds() } returns setOf(1L)
        coEvery { favoriteDao.getAll() } returns (1L..1001L).map { favorite(it, 0L) }
        coEvery { hiddenDao.allMediaIds() } returns emptyList()
        coEvery { albumPhotoDao.allMediaIds() } returns emptyList()
        coEvery { photoTagDao.allMediaIds() } returns emptyList()
        val chunks = mutableListOf<List<Long>>()
        coEvery { favoriteDao.deleteAll(capture(chunks)) } returns Unit
        coEvery { hiddenDao.deleteAll(any<Collection<Long>>()) } returns Unit
        coEvery { albumPhotoDao.deleteByMediaIds(any()) } returns Unit
        coEvery { photoTagDao.deleteByMediaIds(any()) } returns Unit

        val removed = repository.sweepOrphans()

        assertEquals(1000, removed)
        assertEquals(2, chunks.size)
        assertTrue(chunks.all { it.size == 500 })
        assertEquals((2L..1001L).toSet(), chunks.flatten().toSet())
    }

    @Test
    fun `reconcileFavorites below API 30 short-circuits without queries`() = runTest {
        repository.reconcileFavorites(sdkInt = 28)

        coVerify(exactly = 0) { dataSource.queryFavoriteIds() }
        coVerify(exactly = 0) { favoriteDao.getAll() }
    }

    @Test
    fun `reconcileFavorites spares fresh favorites and drops stale ones`() = runTest {
        val now = 1_000_000L
        coEvery { dataSource.queryFavoriteIds() } returns setOf(2L)
        coEvery { favoriteDao.getAll() } returns listOf(
            // App 内刚打的收藏:MediaStore 端 createFavoriteRequest 尚未生效,宽限期保护
            favorite(1L, addedAt = now - 1_000),
            // 仍在 MediaStore 收藏里:保留
            favorite(2L, addedAt = now - 100_000),
            // MediaStore 已取消且超过宽限期:清除
            favorite(3L, addedAt = now - 100_000)
        )
        stubDeletes()

        repository.reconcileFavorites(sdkInt = 30, now = now)

        coVerify(exactly = 1) { favoriteDao.deleteAll(listOf(3L)) }
    }

    @Test
    fun `reconcileFavorites with empty local table makes no deletes`() = runTest {
        coEvery { dataSource.queryFavoriteIds() } returns setOf(1L)
        coEvery { favoriteDao.getAll() } returns emptyList()
        stubDeletes()

        repository.reconcileFavorites(sdkInt = 30)

        coVerify(exactly = 0) { favoriteDao.deleteAll(any<Collection<Long>>()) }
    }
}
