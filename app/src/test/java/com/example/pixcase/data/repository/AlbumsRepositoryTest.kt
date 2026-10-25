package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.AlbumDao
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.entity.AlbumEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 相册仓库覆盖:创建 / 重命名的名字校验、封面自动与顺延、删除时连关联一起清
 * (photo 表无外键级联,计划陷阱 3)。
 */
class AlbumsRepositoryTest {
    private val albumDao = mockk<AlbumDao>(relaxed = true)
    private val albumPhotoDao = mockk<AlbumPhotoDao>(relaxed = true)
    private val repository = AlbumsRepository(albumDao, albumPhotoDao)

    @Test
    fun `createAlbum rejects blank and overlong names`() = runTest {
        assertNull(repository.createAlbum("   ", 0L))
        assertNull(repository.createAlbum("a".repeat(51), 0L))
        coVerify(exactly = 0) { albumDao.insert(any()) }
    }

    @Test
    fun `createAlbum trims and inserts with timestamps`() = runTest {
        coEvery { albumDao.insert(any()) } returns 42L

        val id = repository.createAlbum("  旅行  ", 100L)

        assertEquals(42L, id)
        coVerify {
            albumDao.insert(
                withArg { album ->
                    assertEquals("旅行", album.name)
                    assertEquals(100L, album.createdAt)
                    assertEquals(100L, album.updatedAt)
                    assertNull(album.coverMediaId)
                }
            )
        }
    }

    @Test
    fun `renameAlbum keeps entity identity and refreshes updatedAt`() = runTest {
        val album = AlbumEntity(id = 7L, name = "旧名", coverMediaId = null, createdAt = 1L, updatedAt = 1L)
        coEvery { albumDao.findById(7L) } returns album
        coEvery { albumDao.findByNameExcluding("新名", 7L) } returns null

        val renamed = repository.renameAlbum(7L, "新名", 200L)

        assertEquals(true, renamed)
        coVerify {
            albumDao.update(
                withArg { updated ->
                    assertEquals(7L, updated.id)
                    assertEquals("新名", updated.name)
                    assertEquals(200L, updated.updatedAt)
                }
            )
        }
    }

    @Test
    fun `renameAlbum fails on blank name`() = runTest {
        assertEquals(false, repository.renameAlbum(7L, "  ", 1L))
        coVerify(exactly = 0) { albumDao.update(any()) }
    }

    @Test
    fun `deleteAlbum clears refs before removing the album`() = runTest {
        coEvery { albumDao.findById(7L) } returns mockk()
        val deleteOrder = mutableListOf<String>()
        coEvery { albumPhotoDao.deleteAllInAlbum(7L) } coAnswers {
            deleteOrder.add("refs")
            Unit
        }
        coEvery { albumDao.delete(any()) } coAnswers {
            deleteOrder.add("album")
            Unit
        }

        repository.deleteAlbum(7L)

        assertEquals(listOf("refs", "album"), deleteOrder)
    }

    @Test
    fun `addPhotos sets cover automatically when album has none`() = runTest {
        val album = AlbumEntity(id = 7L, name = "相册", coverMediaId = null, createdAt = 1L, updatedAt = 1L)
        coEvery { albumDao.findById(7L) } returns album
        coEvery { albumPhotoDao.insertAll(any()) } just runs
        coEvery { albumPhotoDao.latestMediaIdInAlbum(7L) } returns 9L

        repository.addPhotos(7L, listOf(8L, 9L), 100L)

        coVerify { albumPhotoDao.insertAll(match { it.size == 2 }) }
        coVerify { albumDao.setCover(7L, 9L, 100L) }
    }

    @Test
    fun `addPhotos keeps manual cover when album already has one`() = runTest {
        val album = AlbumEntity(id = 7L, name = "相册", coverMediaId = 3L, createdAt = 1L, updatedAt = 1L)
        coEvery { albumDao.findById(7L) } returns album
        coEvery { albumPhotoDao.insertAll(any()) } just runs

        repository.addPhotos(7L, listOf(8L), 100L)

        coVerify(exactly = 0) { albumDao.setCover(any(), any(), any()) }
    }

    @Test
    fun `removePhoto rolls cover to the next photo or clears it`() = runTest {
        val album = AlbumEntity(id = 7L, name = "相册", coverMediaId = 5L, createdAt = 1L, updatedAt = 1L)
        coEvery { albumDao.findById(7L) } returns album
        coEvery { albumPhotoDao.delete(7L, 5L) } just runs
        coEvery { albumPhotoDao.latestMediaIdInAlbum(7L) } returnsMany listOf(9L, null)

        repository.removePhoto(7L, 5L, 200L)
        coVerify { albumDao.setCover(7L, 9L, any()) }

        repository.removePhoto(7L, 5L, 200L)
        coVerify { albumDao.clearCover(7L, any()) }
    }

    @Test
    fun `normalizeAlbumName boundary`() {
        assertNotNull(normalizeAlbumName("  相册  "))
        assertNull(normalizeAlbumName(""))
        assertNull(normalizeAlbumName("a".repeat(51)))
        assertEquals("a".repeat(50), normalizeAlbumName("a".repeat(50)))
    }
}
