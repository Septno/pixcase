package com.example.pixcase.ui.feature.timeline

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.paging.PagingData
import com.example.pixcase.data.prefs.UserPreferencesDataSource
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.TagsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
 * 捏合变列数的 ViewModel 覆盖:初始值读偏好、变更落盘、同值不落盘、绝对边界夹取。
 * 按屏幕宽度反推的上限在 UI 层([PhotoGrid])夹取,不在 ViewModel 职责内。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelColumnsTest {
    private val appContext = mockk<Context>(relaxed = true)
    private val repository = mockk<PhotoRepository>(relaxed = true)
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
    }

    @Test
    fun `columnCount initializes from persisted preference`() = runTest(testDispatcher) {
        every { userPreferences.gridColumns() } returns 5

        val vm = TimelineViewModel(appContext, repository, tagsRepository, userPreferences)

        assertEquals(5, vm.columnCount.value)
    }

    @Test
    fun `setColumnCount updates state and persists`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, tagsRepository, userPreferences)

        vm.setColumnCount(4)

        assertEquals(4, vm.columnCount.value)
        verify(exactly = 1) { userPreferences.setGridColumns(4) }
    }

    @Test
    fun `setColumnCount with unchanged value skips persistence`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, tagsRepository, userPreferences)

        vm.setColumnCount(3)

        verify(exactly = 0) { userPreferences.setGridColumns(any()) }
    }

    @Test
    fun `setColumnCount clamps to absolute bounds before persisting`() = runTest(testDispatcher) {
        val vm = TimelineViewModel(appContext, repository, tagsRepository, userPreferences)

        vm.setColumnCount(-1)

        assertEquals(MIN_COLUMNS, vm.columnCount.value)
        verify(exactly = 1) { userPreferences.setGridColumns(MIN_COLUMNS) }

        vm.setColumnCount(MAX_ABSOLUTE_COLUMNS + 1)

        assertEquals(MAX_ABSOLUTE_COLUMNS, vm.columnCount.value)
        verify(exactly = 1) { userPreferences.setGridColumns(MAX_ABSOLUTE_COLUMNS) }
    }
}
