package com.example.pixcase.ui.feature.search

import android.net.Uri
import app.cash.turbine.test
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.SearchHitType
import com.example.pixcase.data.repository.SearchRepository
import com.example.pixcase.data.repository.SearchedPhoto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 搜索 ViewModel:debounce(200ms)后才触发查询、结果流订阅即得、
 * 换词清空选中集、分享走事件通道。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val searchRepository = mockk<SearchRepository>()
    private val photoRepository = mockk<PhotoRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { searchRepository.search(any()) } returns emptyList()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun photo(id: Long) = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "img_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    @Test
    fun `query triggers search only after debounce`() = runTest(testDispatcher) {
        coEvery { searchRepository.search("ab") } returns listOf(
            SearchedPhoto(photo(1L), setOf(SearchHitType.FILENAME))
        )
        val vm = SearchViewModel(searchRepository, photoRepository)

        vm.results.test {
            assertEquals(emptyList<SearchedPhoto>(), awaitItem())
            vm.onQueryChange("a")
            advanceTimeBy(100)
            vm.onQueryChange("ab")
            // debounce 窗口内未到 200ms 不查询
            io.mockk.coVerify(exactly = 0) { searchRepository.search(any()) }
            advanceTimeBy(250)
            advanceUntilIdle()
            io.mockk.coVerify(exactly = 1) { searchRepository.search("ab") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `changing query clears selection`() = runTest(testDispatcher) {
        val vm = SearchViewModel(searchRepository, photoRepository)
        vm.toggleSelection(1L)
        assertEquals(setOf(1L), vm.selectedIds.value)

        vm.onQueryChange("new")

        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `shareSelection emits share event with photo uris`() = runTest(testDispatcher) {
        val photo = photo(9L)
        coEvery { photoRepository.photosByIds(setOf(9L)) } returns listOf(photo)
        val vm = SearchViewModel(searchRepository, photoRepository)
        vm.toggleSelection(9L)

        vm.events.test {
            vm.shareSelection()
            advanceUntilIdle()
            assertEquals(SearchEvent.SharePhotos(listOf(photo.uri)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shareSelection with empty selection is a no-op`() = runTest(testDispatcher) {
        val vm = SearchViewModel(searchRepository, photoRepository)

        vm.shareSelection()
        advanceUntilIdle()

        io.mockk.coVerify(exactly = 0) { photoRepository.photosByIds(any()) }
    }
}
