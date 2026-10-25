package com.example.pixcase.ui.feature.quicksort

import androidx.lifecycle.SavedStateHandle
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.QuickSortSessionTargetEntity
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.QuickSortRepository
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
import org.junit.Before
import org.junit.Test

/**
 * 目标相册选择器的规则覆盖(计划 § 3.5.5):
 * 初始勾选 = 现有 target 顺序;切换按勾选先后追加(position 语义);
 * 新建相册名称校验(非法/重名不入库,合法则创建并自动勾选);
 * 空选择保存先提示、第二次才放行;保存按勾选顺序写 target 表。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickSortTargetPickerViewModelTest {
    private val albumsRepository = mockk<AlbumsRepository>()
    private val quickSortRepository = mockk<QuickSortRepository>()
    private val testDispatcher = StandardTestDispatcher()

    private fun album(id: Long, name: String) = AlbumEntity(
        id = id,
        name = name,
        coverMediaId = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { albumsRepository.observeUserAlbums() } returns flowOf(
            listOf(album(1L, "Source"), album(2L, "TargetA"), album(3L, "TargetB"))
        )
        every { quickSortRepository.observeTargets(any()) } returns flowOf(emptyList())
        coEvery { quickSortRepository.updateTargets(any(), any()) } returns Unit
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(albumId: Long = 1L): QuickSortTargetPickerViewModel {
        val handle = SavedStateHandle(mapOf(QuickSortTargetPickerViewModel.ALBUM_ID_KEY to albumId))
        return QuickSortTargetPickerViewModel(handle, albumsRepository, quickSortRepository)
    }

    @Test
    fun `selection starts from existing targets in order`() = runTest(testDispatcher) {
        every { quickSortRepository.observeTargets(1L) } returns flowOf(
            listOf(QuickSortSessionTargetEntity(1L, 0, 3L), QuickSortSessionTargetEntity(1L, 1, 2L))
        )
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf(3L, 2L), vm.selection.value)
    }

    @Test
    fun `toggle appends on check and removes on uncheck keeping order`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.toggle(2L)
        vm.toggle(3L)
        assertEquals(listOf(2L, 3L), vm.selection.value)

        vm.toggle(2L)
        assertEquals(listOf(3L), vm.selection.value)

        vm.toggle(2L)
        assertEquals("重新勾选追加到末尾", listOf(3L, 2L), vm.selection.value)
    }

    @Test
    fun `createAlbum with invalid name emits event and creates nothing`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.createAlbum("   ")
        advanceUntilIdle()

        coVerify(exactly = 0) { albumsRepository.createAlbum(any(), any()) }
        assertEquals(TargetPickerEvent.InvalidName(duplicate = false), vm.events.value)
    }

    @Test
    fun `createAlbum with duplicate name emits duplicate event`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.createAlbum("TargetA")
        advanceUntilIdle()

        coVerify(exactly = 0) { albumsRepository.createAlbum(any(), any()) }
        assertEquals(TargetPickerEvent.InvalidName(duplicate = true), vm.events.value)
    }

    @Test
    fun `createAlbum with valid name creates and auto-checks`() = runTest(testDispatcher) {
        coEvery { albumsRepository.createAlbum("New", any()) } returns 9L
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(2L)

        vm.createAlbum("New")
        advanceUntilIdle()

        coVerify(exactly = 1) { albumsRepository.createAlbum("New", any()) }
        assertEquals(listOf(2L, 9L), vm.selection.value)
    }

    @Test
    fun `save with empty selection hints first and saves on second tap`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.save()
        advanceUntilIdle()
        assertEquals(TargetPickerEvent.EmptySelectionHint, vm.events.value)
        coVerify(exactly = 0) { quickSortRepository.updateTargets(any(), any()) }

        vm.consumeEvent()
        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { quickSortRepository.updateTargets(1L, emptyList()) }
        assertEquals(TargetPickerEvent.Saved, vm.events.value)
    }

    @Test
    fun `save writes targets in selection order`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.toggle(3L)
        vm.toggle(2L)

        vm.save()
        advanceUntilIdle()

        coVerify(exactly = 1) { quickSortRepository.updateTargets(1L, listOf(3L, 2L)) }
    }
}
