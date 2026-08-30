package com.example.pixcase.ui.feature.timeline

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.paging.PagingData
import com.example.pixcase.core.permission.PermissionUiState
import com.example.pixcase.data.repository.PhotoRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
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

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModelTest {
    private val appContext = mockk<Context>(relaxed = true)
    private val repository = mockk<PhotoRepository>(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockkStatic(ContextCompat::class)
        every { repository.images() } returns flowOf(PagingData.empty())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        unmockkStatic(ContextCompat::class)
    }

    @Test
    fun `init with all permissions granted sets Granted state`() = runTest(testDispatcher) {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        val vm = TimelineViewModel(appContext, repository)

        assertEquals(PermissionUiState.Granted, vm.permissionState.value)
    }

    @Test
    fun `init with missing permissions defaults to NeedsRequest`() = runTest(testDispatcher) {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

        val vm = TimelineViewModel(appContext, repository)

        val state = vm.permissionState.value
        assertTrue("expected NeedsRequest, got $state", state is PermissionUiState.NeedsRequest)
    }

    @Test
    fun `onRequestResult with still missing transitions to PermanentlyDenied`() = runTest(testDispatcher) {
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_DENIED

        val vm = TimelineViewModel(appContext, repository)
        vm.onRequestResult(emptyMap())

        val state = vm.permissionState.value
        assertTrue("expected PermanentlyDenied, got $state", state is PermissionUiState.PermanentlyDenied)
    }

    @Test
    fun `onRequestResult with all granted transitions to Granted`() = runTest(testDispatcher) {
        // 初始 granted
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        val vm = TimelineViewModel(appContext, repository)
        // 即便模拟首次请求回调,缺失列表为空时也是 Granted
        vm.onRequestResult(emptyMap())

        assertEquals(PermissionUiState.Granted, vm.permissionState.value)
    }
}
