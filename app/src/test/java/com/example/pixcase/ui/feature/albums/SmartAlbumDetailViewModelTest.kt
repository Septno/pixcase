package com.example.pixcase.ui.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.PhotoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 智能相册详情 ViewModel 的分流覆盖:kind → 标题资源与 PhotoRepository 数据源一一对应。
 * (收藏/截图内容流由 PhotoRepositorySmartAlbumTest 覆盖,这里只验证委托关系。)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SmartAlbumDetailViewModelTest {
    private val photoRepository = mockk<PhotoRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(kind: String): SmartAlbumDetailViewModel {
        val handle = SavedStateHandle(mapOf(SmartAlbumDetailViewModel.SMART_KIND_KEY to kind))
        return SmartAlbumDetailViewModel(handle, photoRepository)
    }

    @Test
    fun `favorites kind uses favorites title and data source`() = runTest(testDispatcher) {
        every { photoRepository.smartFavorites() } returns flowOf(PagingData.empty<MediaPhoto>())

        val vm = viewModel(SMART_FAVORITES)

        assertEquals(R.string.albums_smart_favorites, vm.titleResId)
        verify(exactly = 1) { photoRepository.smartFavorites() }
        verify(exactly = 0) { photoRepository.smartScreenshots() }
    }

    @Test
    fun `screenshots kind uses screenshots title and data source`() = runTest(testDispatcher) {
        every { photoRepository.smartScreenshots() } returns flowOf(PagingData.empty<MediaPhoto>())

        val vm = viewModel(SMART_SCREENSHOTS)

        assertEquals(R.string.albums_smart_screenshots, vm.titleResId)
        verify(exactly = 1) { photoRepository.smartScreenshots() }
        verify(exactly = 0) { photoRepository.smartFavorites() }
    }
}
