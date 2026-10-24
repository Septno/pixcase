package com.example.pixcase.ui.feature.albums

import com.example.pixcase.data.repository.AlbumsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 相册 Tab ViewModel 的轻量覆盖:非法名走 onInvalid 回调,不触达仓库成功路径。 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsViewModelTest {
    private val albumsRepository = mockk<AlbumsRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createAlbum reports invalid name via callback`() = runTest(testDispatcher) {
        coEvery { albumsRepository.createAlbum("   ", any()) } returns null
        val vm = AlbumsViewModel(albumsRepository)
        var invalid = false

        vm.createAlbum("   ") { invalid = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(invalid)
    }

    @Test
    fun `deleteAlbum delegates to repository`() = runTest(testDispatcher) {
        val vm = AlbumsViewModel(albumsRepository)

        vm.deleteAlbum(7L)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { albumsRepository.deleteAlbum(7L) }
    }
}
