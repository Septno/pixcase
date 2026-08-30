package com.example.pixcase.core.permission

/**
 * 权限 UI 状态机。
 *
 * - Checking:ViewModel 启动时的初始状态,正在调用 checkSelfPermission;
 * - Granted:所有必需权限已授予,渲染主内容;
 * - NeedsRequest:有缺失且系统未永久拒绝,展示带"Allow"按钮的引导页;
 * - PermanentlyDenied:shouldShowRationale == false 且未授予
 *   (典型场景:用户勾选"不再询问"或首次拒绝后再次启动进程),
 *   引导用户去系统设置手动开启。
 */
sealed interface PermissionUiState {
    data object Checking : PermissionUiState

    data object Granted : PermissionUiState

    data class NeedsRequest(val missing: List<String>) : PermissionUiState

    data class PermanentlyDenied(val missing: List<String>) : PermissionUiState
}
