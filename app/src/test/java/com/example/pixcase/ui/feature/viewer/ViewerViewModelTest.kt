package com.example.pixcase.ui.feature.viewer

import android.net.Uri
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ViewerViewModelTest {
    private val dataSource = mockk<MediaStoreDataSource>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun photo(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L,
        width = 400,
        height = 300
    )

    @Test
    fun `starts with no details`() = runTest(testDispatcher) {
        assertNull(ViewerViewModel(dataSource).details.value)
    }

    @Test
    fun `loadDetails publishes the queried photo`() = runTest(testDispatcher) {
        val expected = photo(7L)
        coEvery { dataSource.photoDetails(7L) } returns expected
        val viewModel = ViewerViewModel(dataSource)

        viewModel.loadDetails(7L)
        advanceUntilIdle()

        assertEquals(expected, viewModel.details.value)
    }

    @Test
    fun `loadDetails clears previous details while loading`() = runTest(testDispatcher) {
        coEvery { dataSource.photoDetails(1L) } returns photo(1L)
        coEvery { dataSource.photoDetails(2L) } returns null
        val viewModel = ViewerViewModel(dataSource)
        viewModel.loadDetails(1L)
        advanceUntilIdle()

        viewModel.loadDetails(2L)

        // 换到新照片时先清空,避免上一张的尺寸短暂显示在下一张上。
        assertNull(viewModel.details.value)
    }

    @Test
    fun `same photo is not queried twice`() = runTest(testDispatcher) {
        coEvery { dataSource.photoDetails(any()) } returns null
        val viewModel = ViewerViewModel(dataSource)

        viewModel.loadDetails(7L)
        advanceUntilIdle()
        viewModel.loadDetails(7L)
        advanceUntilIdle()

        coVerify(exactly = 1) { dataSource.photoDetails(7L) }
    }
}
