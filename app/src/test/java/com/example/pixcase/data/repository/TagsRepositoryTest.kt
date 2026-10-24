package com.example.pixcase.data.repository

import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.PhotoTagCrossRef
import com.example.pixcase.data.db.entity.PhotoTagRow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 标签仓库覆盖:创建的名字校验与重名语义、删除时连关联一起清、批量打 / 摘标签。
 */
class TagsRepositoryTest {
    private val tagDao = mockk<TagDao>(relaxed = true)
    private val photoTagDao = mockk<PhotoTagDao>(relaxed = true)
    private val repository = TagsRepository(tagDao, photoTagDao)

    @Test
    fun `create rejects blank and overlong names without touching dao`() = runTest {
        assertNull(repository.create("  ", 0xFF0000FF.toInt(), 0L))
        assertNull(repository.create("a".repeat(31), 0xFF0000FF.toInt(), 0L))
        coVerify(exactly = 0) { tagDao.insert(any()) }
    }

    @Test
    fun `create returns null on duplicate name (IGNORE conflict)`() = runTest {
        coEvery { tagDao.insert(any()) } returns -1L

        assertNull(repository.create("已有标签", 0xFF0000FF.toInt(), 0L))
    }

    @Test
    fun `create returns new id and trims name`() = runTest {
        coEvery { tagDao.insert(any()) } returns 7L

        val id = repository.create("  风景  ", 0xFF00FF00.toInt(), 100L)

        assertEquals(7L, id)
        coVerify {
            tagDao.insert(
                withArg { tag ->
                    assertEquals("风景", tag.name)
                    assertEquals(0xFF00FF00.toInt(), tag.colorArgb)
                }
            )
        }
    }

    @Test
    fun `delete clears tag refs before removing the tag`() = runTest {
        coEvery { tagDao.findById(3L) } returns mockk()
        val order = mutableListOf<String>()
        coEvery { photoTagDao.deleteByTagId(3L) } coAnswers {
            order.add("refs")
            Unit
        }
        coEvery { tagDao.delete(any()) } coAnswers {
            order.add("tag")
            Unit
        }

        repository.delete(3L)

        assertEquals(listOf("refs", "tag"), order)
    }

    @Test
    fun `applyToMedia skips empty and inserts rows for all ids`() = runTest {
        coEvery { photoTagDao.insertAll(any()) } just runs

        repository.applyToMedia(3L, emptyList())
        coVerify(exactly = 0) { photoTagDao.insertAll(any()) }

        repository.applyToMedia(3L, listOf(1L, 2L, 3L))
        coVerify {
            photoTagDao.insertAll(
                match<List<PhotoTagCrossRef>> { it.size == 3 }
            )
        }
    }

    @Test
    fun `rowsForMedia forwards to dao`() = runTest {
        val rows = listOf(PhotoTagRow(1L, 3L), PhotoTagRow(2L, 3L))
        coEvery { photoTagDao.rowsForMedia(listOf(1L, 2L)) } returns rows

        assertEquals(rows, repository.rowsForMedia(listOf(1L, 2L)))
    }

    @Test
    fun `normalizeTagName boundary`() {
        assertEquals("风景", normalizeTagName(" 风景 "))
        assertNull(normalizeTagName(""))
        assertNull(normalizeTagName("a".repeat(31)))
        assertEquals("a".repeat(30), normalizeTagName("a".repeat(30)))
    }
}
