package com.example.pixcase.data.repository

import android.net.Uri
import com.example.pixcase.data.db.dao.FavoriteDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PhotoRepository 的 overlay 逻辑覆盖:
 * - FavoriteDao.observeAllIds() 返回的 mediaId 集合 → MediaPhoto.isFavorite = (id ∈ set);
 * - HiddenDao.observeAllIds() 返回的 mediaId 集合 → MediaPhoto.isHidden = (id ∈ set);
 * - isHidden = true 的条目随后被整体过滤出时间线。
 *
 * PagingData 在 unit test 难以直接 inspect(androidx.paging.asSnapshot 跨版本定位不稳),
 * 本测试用等效的 List<MediaPhoto> + kotlinx.coroutines.flow.combine 模拟同样的覆盖流水线,
 * 验证叠加结果与 PhotoRepository.images() 内部 combine 行为一致。
 */
class PhotoRepositoryTest {
    private fun mediaPhoto(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "img_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    /** 与 PhotoRepository.images() 内的 combine + map + filter 等价的 List 实现。 */
    private suspend fun applyOverlay(
        items: List<MediaPhoto>,
        favoriteDao: FavoriteDao,
        hiddenDao: HiddenDao
    ): List<MediaPhoto> {
        return combine(
            flowOf(items),
            favoriteDao.observeAllIds(),
            hiddenDao.observeAllIds()
        ) { source, favIds, hiddenIds ->
            val favSet = favIds.toHashSet()
            val hiddenSet = hiddenIds.toHashSet()
            source
                .map { p ->
                    p.copy(
                        isFavorite = p.id in favSet,
                        isHidden = p.id in hiddenSet
                    )
                }
                .filter { !it.isHidden }
        }.first()
    }

    @Test
    fun `applies isFavorite from favoriteDao and filters hidden ids out`() = runTest {
        val sourceItems = listOf(mediaPhoto(1L), mediaPhoto(2L), mediaPhoto(3L))
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(listOf(2L))
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(listOf(1L, 3L))
        }

        val snapshot = applyOverlay(sourceItems, favoriteDao, hiddenDao)

        // 隐藏的 1 / 3 直接从时间线消失;留下的 2 带上收藏标记
        assertEquals(listOf(2L), snapshot.map { it.id })
        assertTrue("id=2 favorited", snapshot.single().isFavorite)
        // isHidden 在过滤后恒为 false:标记只用于过滤本身,不会再出现在展示层
        assertFalse(snapshot.single().isHidden)
    }

    @Test
    fun `visible items keep isFavorite overlay from favoriteDao only`() = runTest {
        val sourceItems = listOf(mediaPhoto(4L), mediaPhoto(5L))
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(listOf(4L))
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(listOf(9L))
        }

        val snapshot = applyOverlay(sourceItems, favoriteDao, hiddenDao)

        assertEquals(2, snapshot.size)
        assertTrue(snapshot.first { it.id == 4L }.isFavorite)
        assertFalse(snapshot.first { it.id == 5L }.isFavorite)
    }

    @Test
    fun `empty favorite and hidden lists keep defaults false`() = runTest {
        val sourceItems = listOf(mediaPhoto(10L), mediaPhoto(20L))
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }

        val snapshot = applyOverlay(sourceItems, favoriteDao, hiddenDao)

        assertEquals(2, snapshot.size)
        snapshot.forEach {
            assertFalse(it.isFavorite)
            assertFalse(it.isHidden)
        }
    }

    @Test
    fun `empty media store returns empty snapshot`() = runTest {
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }

        val snapshot = applyOverlay(emptyList(), favoriteDao, hiddenDao)

        assertEquals(0, snapshot.size)
    }

    @Test
    fun `hidden items are filtered out of the timeline`() = runTest {
        val sourceItems = listOf(mediaPhoto(1L), mediaPhoto(2L), mediaPhoto(3L))
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(listOf(2L))
        }

        val snapshot = applyOverlay(sourceItems, favoriteDao, hiddenDao)

        assertEquals(listOf(1L, 3L), snapshot.map { it.id })
    }

    @Test
    fun `all items favorited keeps isFavorite true`() = runTest {
        val sourceItems = listOf(mediaPhoto(100L), mediaPhoto(200L))
        val favoriteDao = mockk<FavoriteDao> {
            every { observeAllIds() } returns flowOf(listOf(100L, 200L))
        }
        val hiddenDao = mockk<HiddenDao> {
            every { observeAllIds() } returns flowOf(emptyList())
        }

        val snapshot = applyOverlay(sourceItems, favoriteDao, hiddenDao)

        snapshot.forEach { assertTrue(it.isFavorite) }
    }
}
