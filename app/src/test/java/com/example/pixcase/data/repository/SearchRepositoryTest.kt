package com.example.pixcase.data.repository

import android.net.Uri
import com.example.pixcase.data.db.dao.AlbumPhotoDao
import com.example.pixcase.data.db.dao.HiddenDao
import com.example.pixcase.data.db.dao.PhotoTagDao
import com.example.pixcase.data.db.dao.SearchHistoryDao
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import com.example.pixcase.data.db.entity.TagEntity
import com.example.pixcase.data.db.entity.TagUsageRow
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 搜索聚合的 JVM 覆盖:三类命中合并去重、命中方式集合、时间基准倒序、
 * 空串短路、纯字面量匹配(通配符不生效)。
 */
class SearchRepositoryTest {
    private val dataSource = mockk<MediaStoreDataSource>()
    private val albumsRepository = mockk<AlbumsRepository>()
    private val albumPhotoDao = mockk<AlbumPhotoDao>()
    private val photoTagDao = mockk<PhotoTagDao>()
    private val tagDao = mockk<TagDao>()
    private val searchHistoryDao = mockk<SearchHistoryDao>()
    private val hiddenDao = mockk<HiddenDao>()
    private val photoRepository = mockk<PhotoRepository>()
    private val repository = SearchRepository(
        dataSource = dataSource,
        albumsRepository = albumsRepository,
        albumPhotoDao = albumPhotoDao,
        photoTagDao = photoTagDao,
        tagDao = tagDao,
        photoRepository = photoRepository,
        searchHistoryDao = searchHistoryDao,
        hiddenDao = hiddenDao
    )

    @Before
    fun setup() {
        coEvery { dataSource.queryByDisplayNameFragment(any()) } returns emptyList()
        every { albumsRepository.observeUserAlbums() } returns flowOf(emptyList())
        every { tagDao.observeAll() } returns flowOf(emptyList())
        coEvery { albumPhotoDao.mediaIdsInAlbums(any()) } returns emptyList()
        coEvery { photoTagDao.mediaIdsForTags(any()) } returns emptyList()
        coEvery { photoRepository.photosByIds(any()) } returns emptyList()
        coEvery { hiddenDao.allMediaIds() } returns emptyList()
        coEvery { photoTagDao.tagUsageCounts() } returns emptyList()
        coEvery { photoTagDao.rowsForMedia(any()) } returns emptyList()
        coEvery { searchHistoryDao.insert(any()) } returns Unit
        coEvery { searchHistoryDao.trimTo(any()) } returns Unit
        coEvery { searchHistoryDao.delete(any()) } returns Unit
        every { searchHistoryDao.observeRecent() } returns flowOf(emptyList())
    }

    private fun photo(id: Long, name: String = "img_$id.jpg", addedSec: Long = id): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = name,
        mimeType = "image/jpeg",
        dateAddedSec = addedSec
    )

    @Test
    fun `blank query short-circuits without touching sources`() = runTest {
        assertTrue(repository.search("   ").isEmpty())
        coEvery { dataSource.queryByDisplayNameFragment(any()) } returns emptyList()
        io.mockk.coVerify(exactly = 0) { dataSource.queryByDisplayNameFragment(any()) }
    }

    @Test
    fun `filename album and tag hits merge with hit type sets`() = runTest {
        every { albumsRepository.observeUserAlbums() } returns flowOf(
            listOf(AlbumEntity(2L, "Summer", null, 0L, 0L))
        )
        every { tagDao.observeAll() } returns flowOf(
            listOf(TagEntity(3L, "sunset", 0, createdAt = 0L))
        )
        // 文件名命中 1;相册成员 1(挂相册 2);标签成员 1(挂标签 3)
        coEvery { dataSource.queryByDisplayNameFragment("su") } returns listOf(photo(1L, "sunset_a.jpg"))
        coEvery { albumPhotoDao.mediaIdsInAlbums(listOf(2L)) } returns listOf(1L, 4L)
        coEvery { photoTagDao.mediaIdsForTags(listOf(3L)) } returns listOf(5L)
        // Set 迭代序不定,photosByIds 按任意入参顺序返回三张
        coEvery { photoRepository.photosByIds(any()) } returns listOf(
            photo(1L), photo(4L, "travel.jpg"), photo(5L, "tagged.jpg")
        )

        val results = repository.search("su")

        // 照片 1 三路合并只出现一次;4 = 相册命中;5 = 标签命中
        assertEquals(3, results.size)
        val byId = results.associateBy { it.photo.id }
        assertEquals(
            setOf(SearchHitType.FILENAME, SearchHitType.ALBUM),
            byId.getValue(1L).hitTypes
        )
        assertEquals(setOf(SearchHitType.ALBUM), byId.getValue(4L).hitTypes)
        assertEquals(setOf(SearchHitType.TAG), byId.getValue(5L).hitTypes)
    }

    @Test
    fun `results sort by timeline time basis descending`() = runTest {
        coEvery { dataSource.queryByDisplayNameFragment("q") } returns listOf(
            photo(1L, addedSec = 100L),
            photo(2L, addedSec = 900L),
            photo(3L, addedSec = 500L)
        )

        val results = repository.search("q")

        assertEquals(listOf(2L, 3L, 1L), results.map { it.photo.id })
    }

    @Test
    fun `fragment is passed through to the data source untouched`() = runTest {
        // 通配符的字面量化在数据源内做(剥 % / _);仓库层只透传
        repository.search("%")

        io.mockk.coVerify(exactly = 1) { dataSource.queryByDisplayNameFragment("%") }
    }

    @Test
    fun `case insensitive matching for album and tag names`() = runTest {
        every { albumsRepository.observeUserAlbums() } returns flowOf(
            listOf(AlbumEntity(2L, "Travel", null, 0L, 0L))
        )
        coEvery { albumPhotoDao.mediaIdsInAlbums(listOf(2L)) } returns listOf(7L)
        coEvery { photoRepository.photosByIds(listOf(7L)) } returns listOf(photo(7L))

        val results = repository.search("tRAVEL")

        assertEquals(1, results.size)
        assertEquals(setOf(SearchHitType.ALBUM), results.first().hitTypes)
    }

    @Test
    fun `recordQuery dedupes to top and trims beyond capacity`() = runTest {
        val inserted = mutableListOf<SearchHistoryEntity>()
        coEvery { searchHistoryDao.insert(any()) } answers {
            inserted.add(firstArg())
            Unit
        }

        repository.recordQuery("a", now = 1L)
        repository.recordQuery("b", now = 2L)
        // 同词重搜:先删旧再插 = 去重置顶(每次记录都先删同词,共 2 次)
        repository.recordQuery("a", now = 3L)

        io.mockk.coVerify(exactly = 2) { searchHistoryDao.delete("a") }
        io.mockk.coVerify(atLeast = 3) { searchHistoryDao.trimTo(20) }
        assertEquals(listOf("a", "b", "a"), inserted.map { it.query })
    }

    @Test
    fun `recordQuery ignores blank input`() = runTest {
        repository.recordQuery("   ")

        io.mockk.coVerify(exactly = 0) { searchHistoryDao.insert(any()) }
    }

    @Test
    fun `suggestions rank tags by usage then append albums`() = runTest {
        every { tagDao.observeAll() } returns flowOf(
            listOf(TagEntity(1L, "sunset", 0, 0L), TagEntity(2L, "summer", 0, 0L))
        )
        every { albumsRepository.observeUserAlbums() } returns flowOf(
            listOf(AlbumEntity(9L, "summer-trip", null, 0L, 0L))
        )
        coEvery { photoTagDao.tagUsageCounts() } returns listOf(
            TagUsageRow(1L, 3),
            TagUsageRow(2L, 7)
        )

        val suggestions = repository.suggestions("su")

        // 标签在前按用量降序(summer 7 > sunset 3),相册殿后
        assertEquals(
            listOf(
                SearchSuggestion("summer", isTag = true),
                SearchSuggestion("sunset", isTag = true),
                SearchSuggestion("summer-trip", isTag = false)
            ),
            suggestions
        )
    }
}
