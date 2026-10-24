package com.example.pixcase.ui.feature.timeline

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.prefs.UserPreferencesDataSource
import com.example.pixcase.data.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 多选模式与图像操作(阶段 2)的 ViewModel 覆盖。
 *
 * JVM 单测里 Build.VERSION.SDK_INT = 0,deleteSelection 走 API 29 及以下分支:
 * 先发 [TimelineEvent.ConfirmPermanentDelete],等 App 内确认后再真正删除;
 * API 30+ 的系统确认框(LaunchDeleteConsent / onSystemDeleteApproved)依赖真机,
 * 不在 JVM 覆盖范围。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelSelectionTest {
    private val appContext = mockk<Context>(relaxed = true)
    private val repository = mockk<PhotoRepository>(relaxed = true)
    private val userPreferences = mockk<UserPreferencesDataSource>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED
        every { repository.images() } returns flowOf(PagingData.empty())
        every { userPreferences.gridColumns() } returns 3
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `selectPhoto enters selection with the tapped id`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, userPreferences)

        vm.selectPhoto(1L)

        assertEquals(setOf(1L), vm.selectedIds.value)
    }

    @Test
    fun `togglePhotoSelection removes when present and empties selection`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.togglePhotoSelection(2L)

        assertEquals(setOf(1L), vm.selectedIds.value)

        vm.togglePhotoSelection(1L)

        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `clearSelection exits selection mode`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)

        vm.clearSelection()

        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `toggleFavoriteForSelection favorites when selection has unfavorited items`() = runTest(testDispatcher) {
        coEvery { repository.favoritesOf(setOf(1L, 2L)) } returns emptySet()
        coEvery { repository.setFavorite(setOf(1L, 2L), true) } returns null
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.toggleFavoriteForSelection()
            assertEquals(TimelineEvent.Message(R.string.msg_favorited, 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.setFavorite(setOf(1L, 2L), true) }
    }

    @Test
    fun `toggleFavoriteForSelection unfavorites when all selected are favorited`() = runTest(testDispatcher) {
        coEvery { repository.favoritesOf(setOf(1L)) } returns setOf(1L)
        coEvery { repository.setFavorite(setOf(1L), false) } returns null
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)

        vm.events.test {
            vm.toggleFavoriteForSelection()
            assertEquals(TimelineEvent.Message(R.string.msg_unfavorited, 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { repository.setFavorite(setOf(1L), false) }
    }

    @Test
    fun `hideSelection hides and clears selection`() = runTest(testDispatcher) {
        coEvery { repository.hidePhotos(setOf(3L)) } just runs
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(3L)

        vm.events.test {
            vm.hideSelection()
            assertEquals(TimelineEvent.Message(R.string.msg_hidden, 1), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(vm.selectedIds.value.isEmpty())
        coVerify(exactly = 1) { repository.hidePhotos(setOf(3L)) }
    }

    @Test
    fun `deleteSelection on legacy sdk asks for permanent delete confirmation first`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.deleteSelection()
            assertEquals(TimelineEvent.ConfirmPermanentDelete, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { repository.deletePhotos(any()) }
    }

    @Test
    fun `deleteSelectionConfirmed performs direct delete and clears selection`() = runTest(testDispatcher) {
        val outcome = MediaStorePhotoWriter.DeleteOutcome.Completed(2, listOf(1L, 2L))
        coEvery { repository.deletePhotos(setOf(1L, 2L)) } returns outcome
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.deleteSelectionConfirmed()
            assertEquals(TimelineEvent.Message(R.string.msg_deleted, 2), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `deleteSelectionConfirmed reports failure when nothing was deleted`() = runTest(testDispatcher) {
        val outcome = MediaStorePhotoWriter.DeleteOutcome.Completed(0, emptyList())
        coEvery { repository.deletePhotos(setOf(1L)) } returns outcome
        val vm = TimelineViewModel(appContext, repository, userPreferences)
        vm.selectPhoto(1L)

        vm.events.test {
            vm.deleteSelectionConfirmed()
            assertEquals(TimelineEvent.Message(R.string.msg_delete_failed, 0), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
