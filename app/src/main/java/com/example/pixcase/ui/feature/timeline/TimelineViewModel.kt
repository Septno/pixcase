package com.example.pixcase.ui.feature.timeline

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pixcase.core.permission.PermissionUiState
import com.example.pixcase.core.permission.requiredMediaPermissions
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 时间线 ViewModel。
 *
 * 持有两类状态:
 * - permissionState:权限引导 UI 的状态机(Checking / Granted / NeedsRequest / PermanentlyDenied);
 * - pagerFlow:PhotoRepository.images() 在 viewModelScope 内 cachedIn,PagingData 跨配置变更复用。
 *
 * 权限状态机约定(1.1 简化,1.3+ 引入 Activity 上下文后细化):
 * - 初始检查(missing 列表非空)默认走 NeedsRequest;
 * - onRequestResult 回调后 [hasRequestedBefore] 置 true,下次 refresh 把状态推到 PermanentlyDenied
 *   (因为系统不再弹窗 → UI 引导用户去设置);
 * - Android 的 shouldShowRequestPermissionRationale 是 Activity-only API,
 *   本 ViewModel 持有 ApplicationContext 不应访问;1.3+ 在 Composable 层拿到 Activity 后
 *   再做更精确的"系统是否仍允许弹窗"判定,这里用 session 级 hasRequestedBefore 兜底。
 */
@HiltViewModel
class TimelineViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: PhotoRepository
) : ViewModel() {

    val pagerFlow: Flow<PagingData<MediaPhoto>> = repository.images().cachedIn(viewModelScope)

    private val _permissionState = MutableStateFlow<PermissionUiState>(PermissionUiState.Checking)
    val permissionState: StateFlow<PermissionUiState> = _permissionState.asStateFlow()

    /** 本次进程生命周期内是否已经触发过权限请求;true 后再 refresh 就把状态推到 PermanentlyDenied。 */
    private var hasRequestedBefore: Boolean = false

    init {
        refreshPermission()
    }

    /** 权限检查。hasRequestedBefore 决定缺失权限时显示 NeedsRequest 还是 PermanentlyDenied。 */
    fun refreshPermission() {
        val missing = currentMissingPermissions()
        _permissionState.value = when {
            missing.isEmpty() -> PermissionUiState.Granted
            hasRequestedBefore -> PermissionUiState.PermanentlyDenied(missing)
            else -> PermissionUiState.NeedsRequest(missing)
        }
    }

    /** 系统权限请求回调后调用;granted map 不直接用,以 checkSelfPermission 当前结果为准。 */
    fun onRequestResult(@Suppress("UNUSED_PARAMETER") granted: Map<String, Boolean>) {
        hasRequestedBefore = true
        refreshPermission()
    }

    private fun currentMissingPermissions(): List<String> = requiredMediaPermissions(Build.VERSION.SDK_INT)
        .filter { ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED }
}
