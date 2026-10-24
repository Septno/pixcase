package com.example.pixcase.ui.feature.timeline

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.paging.PagingData
import app.cash.turbine.test
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.PhotoTagRow
import com.example.pixcase.data.db.entity.TagEntity
import com.example.pixcase.data.mediastore.MediaStorePhotoWriter
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.prefs.UserPreferencesDataSource
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.TagsRepository
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
 * 分享 / 设为壁纸 / 重命名的 ViewModel 覆盖。
 *
 * JVM 里 SDK_INT = 0:renameSelection 的 NeedsConsent 分支(createWriteRequest)依赖真机
 * MediaProvider,不在 JVM 覆盖范围;这里覆盖单张语义校验、事件发出与授权后的重试编排。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelActionsTest {
    private val appContext = mockk<Context>(relaxed = true)
    private val repository = mockk<PhotoRepository>(relaxed = true)
    private val albumsRepository = mockk<AlbumsRepository>(relaxed = true)
    private val tagsRepository = mockk<TagsRepository>(relaxed = true)
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
        unmockkStatic(WallpaperManager::class)
    }

    private fun mediaPhoto(id: Long): MediaPhoto = MediaPhoto(
        id = id,
        uri = mockk<Uri>(relaxed = true),
        displayName = "IMG_$id.jpg",
        mimeType = "image/jpeg",
        dateAddedSec = 0L
    )

    @Test
    fun `shareSelection emits uris of the selected photos`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(1L, 2L)) } returns listOf(mediaPhoto(1L), mediaPhoto(2L))
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.shareSelection()
            val event = awaitItem()
            assertTrue(event is TimelineEvent.SharePhotos)
            assertEquals(2, (event as TimelineEvent.SharePhotos).uris.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setWallpaperForSelection requires exactly one photo`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(1L, 2L)) } returns listOf(mediaPhoto(1L), mediaPhoto(2L))
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.setWallpaperForSelection()
            assertEquals(TimelineEvent.Message(R.string.msg_only_one_photo), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setWallpaperForSelection with one photo launches system picker`() = runTest(testDispatcher) {
        val photo = mediaPhoto(1L)
        coEvery { repository.photosByIds(setOf(1L)) } returns listOf(photo)
        val pickerIntent = mockk<Intent>(relaxed = true)
        // VM 会链式 addFlags(FLAG_GRANT_READ_URI_PERMISSION):relaxed mock 默认返回新实例,
        // 必须桩成返回自身,事件里携带的才是同一个 Intent
        every { pickerIntent.addFlags(any()) } returns pickerIntent
        mockkStatic(WallpaperManager::class)
        every { WallpaperManager.getInstance(any()) } returns mockk {
            every { getCropAndSetWallpaperIntent(any()) } returns pickerIntent
        }
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)

        vm.events.test {
            vm.setWallpaperForSelection()
            assertEquals(TimelineEvent.LaunchSetWallpaper(pickerIntent), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestRename with one photo opens dialog with base name`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(7L)) } returns listOf(mediaPhoto(7L))
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(7L)

        vm.events.test {
            vm.requestRename()
            assertEquals(TimelineEvent.OpenRenameDialog("IMG_7"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestRename with multiple photos asks for single selection`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(1L, 2L)) } returns listOf(mediaPhoto(1L), mediaPhoto(2L))
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.requestRename()
            assertEquals(TimelineEvent.Message(R.string.msg_only_one_photo), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renameSelection reattaches extension and reports success`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(7L)) } returns listOf(mediaPhoto(7L))
        coEvery { repository.renamePhoto(7L, "holiday.jpg") } returns
            MediaStorePhotoWriter.RenameOutcome.Done(true)
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(7L)

        vm.events.test {
            vm.renameSelection(" holiday ")
            assertEquals(TimelineEvent.Message(R.string.msg_renamed), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `renameSelection needing write consent retries after approval`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(7L)) } returns listOf(mediaPhoto(7L))
        coEvery { repository.renamePhoto(7L, "holiday.jpg") } returns
            MediaStorePhotoWriter.RenameOutcome.NeedsConsent(mockk()) andThen
            MediaStorePhotoWriter.RenameOutcome.Done(true)
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(7L)

        vm.events.test {
            vm.renameSelection("holiday")
            assertTrue(awaitItem() is TimelineEvent.LaunchWriteConsent)
            cancelAndIgnoreRemainingEvents()
        }

        vm.events.test {
            vm.onWriteConsentApproved()
            assertEquals(TimelineEvent.Message(R.string.msg_renamed), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `write consent denied drops the pending rename silently`() = runTest(testDispatcher) {
        coEvery { repository.photosByIds(setOf(7L)) } returns listOf(mediaPhoto(7L))
        coEvery { repository.renamePhoto(7L, "holiday.jpg") } returns
            MediaStorePhotoWriter.RenameOutcome.NeedsConsent(mockk())
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(7L)

        vm.events.test {
            vm.renameSelection("holiday")
            assertTrue(awaitItem() is TimelineEvent.LaunchWriteConsent)
            cancelAndIgnoreRemainingEvents()
        }

        vm.onWriteConsentDenied()
        vm.events.test {
            vm.onWriteConsentApproved()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ---- 打标签(阶段 3) ----

    private fun tag(id: Long) = TagEntity(id = id, name = "标签$id", colorArgb = 0, createdAt = 0L)

    @Test
    fun `requestTagSelection checks only the intersection across selected photos`() = runTest(testDispatcher) {
        // 照片 1 带标签 {3, 5},照片 2 带标签 {5}:交集只有 5
        coEvery { tagsRepository.observeAll() } returns flowOf(listOf(tag(3), tag(5)))
        coEvery { tagsRepository.rowsForMedia(setOf(1L, 2L)) } returns
            listOf(PhotoTagRow(1L, 3L), PhotoTagRow(1L, 5L), PhotoTagRow(2L, 5L))
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.events.test {
            vm.requestTagSelection()
            val event = awaitItem()
            assertTrue(event is TimelineEvent.OpenTagDialog)
            assertEquals(setOf(5L), (event as TimelineEvent.OpenTagDialog).checkedTagIds)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `applyTags diffs against the intersection snapshot`() = runTest(testDispatcher) {
        coEvery { tagsRepository.observeAll() } returns flowOf(listOf(tag(3), tag(5)))
        coEvery { tagsRepository.rowsForMedia(setOf(1L, 2L)) } returns
            listOf(PhotoTagRow(1L, 3L), PhotoTagRow(1L, 5L), PhotoTagRow(2L, 5L))
        coEvery { tagsRepository.applyToMedia(any(), any()) } just runs
        coEvery { tagsRepository.removeFromMedia(any(), any()) } just runs
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)
        vm.selectPhoto(2L)

        vm.requestTagSelection() // 快照交集 {5}
        testDispatcher.scheduler.advanceUntilIdle()
        vm.applyTags(setOf(3L)) // 勾上 3;取消 5(不再勾选)→ 新增 3、移除 5
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { tagsRepository.applyToMedia(3L, setOf(1L, 2L)) }
        coVerify(exactly = 1) { tagsRepository.removeFromMedia(5L, setOf(1L, 2L)) }
        assertTrue(vm.selectedIds.value.isEmpty())
    }

    @Test
    fun `createTagInDialog reports duplicate names without emitting dialog`() = runTest(testDispatcher) {
        coEvery { tagsRepository.create("已有", any(), any()) } returns null
        coEvery { tagsRepository.observeAll() } returns flowOf(emptyList())
        coEvery { tagsRepository.rowsForMedia(setOf(1L)) } returns emptyList()
        val vm = TimelineViewModel(appContext, repository, albumsRepository, tagsRepository, userPreferences)
        vm.selectPhoto(1L)

        vm.events.test {
            vm.createTagInDialog("已有")
            assertEquals(TimelineEvent.Message(R.string.msg_tag_duplicate), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
