package com.example.pixcase.ui.feature.albums

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
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
 * 相册详情页 ViewModel 的多选语义与数据流覆盖:
 * - 多选集合的进入 / 切换 / 清空(与时间线同一套约定);
 * - 移出 = 逐张解除 album_photo 关联 + 清空选中 + Snackbar 事件(空选中集不触达仓库);
 * - 设为封面 = 仅选中 1 张时生效;
 * - photos 流按 Room mediaIds 顺序重排,已被系统删除的照片(补查缺席)跳过。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {
    private val albumsRepository = mockk<AlbumsRepository>()
    private val photoRepository = mockk<PhotoRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // 构造 VM 时 stateIn 包装即调用这两个入口;需要特定值的用例在各自测试里覆盖
        every { albumsRepository.observeUserAlbums() } returns flowOf(emptyList())
        every { albumsRepository.observeMediaIds(any()) } returns flowOf(emptyList())
        coEvery { albumsRepository.removePhoto(any(), any(), any()) } returns Unit
        coEvery { albumsRepository.setCover(any(), any(), any()) } returns Unit
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(albumId: Long = 5L): AlbumDetailViewModel {
        val handle = SavedStateHandle(mapOf(AlbumDetailViewModel.ALBUM_ID_KEY to albumId))
        return AlbumDetailViewModel(handle, albumsRepository, photoRepository)
    }

    private fun photo(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "img_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    @Test
    fun `selection enters by long press toggles and clears`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.selectPhoto(1L)
        vm.selectPhoto(2L)
        assertEquals(setOf(1L, 2L), vm.selectedIds.value)

        vm.togglePhotoSelection(1L)
        assertEquals(setOf(2L), vm.selectedIds.value)

        vm.togglePhotoSelection(2L)
        assertTrue("选空应自动退出多选", vm.selectedIds.value.isEmpty())

        vm.selectPhoto(3L)
        vm.clearSelection()
        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `removeSelection unbinds each selected photo and clears selection`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.removeSelection()
        advanceUntilIdle()

        coVerify(exactly = 1) { albumsRepository.removePhoto(5L, 1L, any()) }
        coVerify(exactly = 1) { albumsRepository.removePhoto(5L, 2L, any()) }
        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `removeSelection emits message with removed count`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectPhoto(1L)

        vm.events.test {
            vm.removeSelection()
            advanceUntilIdle()
            assertEquals(AlbumDetailEvent.Message(R.string.msg_removed_from_album, 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `removeSelection without selection does not touch repository`() = runTest(testDispatcher) {
        val vm = viewModel()

        vm.removeSelection()
        advanceUntilIdle()

        coVerify(exactly = 0) { albumsRepository.removePhoto(any(), any(), any()) }
    }

    @Test
    fun `setCoverForSelection uses the single selected photo`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectPhoto(9L)

        vm.events.test {
            vm.setCoverForSelection()
            advanceUntilIdle()
            coVerify(exactly = 1) { albumsRepository.setCover(5L, 9L, any()) }
            assertTrue("设为封面后应退出多选", vm.selectedIds.value.isEmpty())
            assertEquals(AlbumDetailEvent.Message(R.string.msg_cover_set), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setCoverForSelection with multiple selected is a no-op`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.setCoverForSelection()
        advanceUntilIdle()

        coVerify(exactly = 0) { albumsRepository.setCover(any(), any(), any()) }
        assertEquals(setOf(1L, 2L), vm.selectedIds.value)
    }

    @Test
    fun `photos follow Room mediaIds order and skip missing photos`() = runTest(testDispatcher) {
        every { albumsRepository.observeMediaIds(5L) } returns flowOf(listOf(3L, 1L))
        coEvery { photoRepository.photosByIds(listOf(3L, 1L)) } returns listOf(photo(1L), photo(3L))
        val vm = viewModel()

        vm.photos.test {
            assertEquals(emptyList<MediaPhoto>(), awaitItem())
            advanceUntilIdle()
            assertEquals(listOf(3L, 1L), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
