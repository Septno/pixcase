package com.example.pixcase.ui.feature.quicksort

import androidx.lifecycle.SavedStateHandle
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.db.entity.QuickSortSessionEntity
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.QuickSortRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
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
 * 快速整理入口与配置流的阶段状态机覆盖(计划 § 3.5.3 的分流规则):
 * 无 session / 已完成 → 配置;未完成 → 选择 Sheet;继续 = 原样进入;
 * 从头 = 重置后进入;新建 = 创建后进入;退出保存翻页位置后回调返回。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuickSortViewModelTest {
    private val quickSortRepository = mockk<QuickSortRepository>()
    private val albumsRepository = mockk<AlbumsRepository>()
    private val photoRepository = mockk<PhotoRepository>()
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // 队列重载的默认 stub(需要特定值的用例在各自测试里覆盖)
        coEvery { quickSortRepository.pendingMediaIds(any()) } returns emptyList()
        coEvery { photoRepository.photosByIds(any()) } returns emptyList()
        every { albumsRepository.observeUserAlbums() } returns flowOf(
            listOf(
                album(id = 1L, name = "Source"),
                album(id = 2L, name = "TargetA"),
                album(id = 3L, name = "TargetB")
            )
        )
        every { quickSortRepository.observeSession(any()) } returns flowOf(null)
        coEvery { quickSortRepository.resetSession(any(), any()) } returns Unit
        coEvery { quickSortRepository.createSession(any(), any(), any()) } returns Unit
        coEvery { quickSortRepository.savePosition(any(), any(), any()) } returns Unit
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun album(id: Long, name: String) = AlbumEntity(
        id = id,
        name = name,
        coverMediaId = null,
        createdAt = 0L,
        updatedAt = 0L
    )

    private fun session(completed: Boolean = false) = QuickSortSessionEntity(
        albumId = 1L,
        startedAt = 0L,
        lastActiveAt = 0L,
        totalCount = 5,
        processedCount = if (completed) 5 else 2,
        currentIndex = 2,
        isCompleted = completed
    )

    private fun viewModel(albumId: Long = 1L): QuickSortViewModel {
        val handle = SavedStateHandle(mapOf(QuickSortViewModel.ALBUM_ID_KEY to albumId))
        return QuickSortViewModel(handle, quickSortRepository, albumsRepository, photoRepository)
    }

    @Test
    fun `no session routes to config`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns null
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(QuickSortStage.NeedsConfig, vm.stage.value)
    }

    @Test
    fun `unfinished session routes to choice`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns session(completed = false)
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(QuickSortStage.NeedsChoice, vm.stage.value)
    }

    @Test
    fun `completed session routes to config for a fresh round`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns session(completed = true)
        val vm = viewModel()

        advanceUntilIdle()

        assertEquals(QuickSortStage.NeedsConfig, vm.stage.value)
    }

    @Test
    fun `continue keeps session untouched and enters active`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns session(completed = false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.continueSession()
        advanceUntilIdle()

        assertEquals(QuickSortStage.Active, vm.stage.value)
        coVerify(exactly = 0) { quickSortRepository.resetSession(any(), any()) }
    }

    @Test
    fun `restart resets session then enters active`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns session(completed = false)
        val vm = viewModel()
        advanceUntilIdle()

        vm.restartFromBeginning()
        advanceUntilIdle()

        assertEquals(QuickSortStage.Active, vm.stage.value)
        coVerify(exactly = 1) { quickSortRepository.resetSession(1L, any()) }
    }

    @Test
    fun `startSession creates with chosen targets and enters active`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.startSession(listOf(3L, 2L))
        advanceUntilIdle()

        assertEquals(QuickSortStage.Active, vm.stage.value)
        coVerify(exactly = 1) { quickSortRepository.createSession(1L, listOf(3L, 2L), any()) }
    }

    @Test
    fun `exit saves position then navigates back`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns null
        val vm = viewModel()
        advanceUntilIdle()
        var navigated = false

        vm.exit(currentIndex = 4) { navigated = true }
        advanceUntilIdle()

        coVerify(exactly = 1) { quickSortRepository.savePosition(1L, 4, any()) }
        assertTrue(navigated)
    }

    @Test
    fun `candidate albums exclude the sorted album itself`() = runTest(testDispatcher) {
        coEvery { quickSortRepository.session(1L) } returns null
        val vm = viewModel(albumId = 1L)
        advanceUntilIdle()

        // WhileSubscribed 的 stateIn 需要订阅才计算;stateIn 总先发初值(空表),跳过后取上游值
        val candidates = vm.candidateAlbums.drop(1).first()
        val name = vm.albumName.drop(1).first()

        assertEquals(listOf(2L, 3L), candidates.map { it.id })
        assertEquals("Source", name)
    }
}
