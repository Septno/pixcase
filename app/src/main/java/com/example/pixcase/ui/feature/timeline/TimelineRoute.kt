package com.example.pixcase.ui.feature.timeline

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.pixcase.core.permission.PermissionGate
import com.example.pixcase.core.permission.appSettingsIntent

/**
 * 时间线路由。
 *
 * 结构:PermissionGate 三态门 + 授权后的照片墙网格([PhotoGrid])。
 * 点击照片暂不响应 —— 全屏查看器是后续增量,这里不预留空回调。
 */
@Composable
fun TimelineRoute(viewModel: TimelineViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val packageName = context.packageName

    // 用户点 "Open settings" 跳去系统设置授权后返回,权限弹窗回调不会再触发,
    // 必须借 ON_RESUME 重新检查权限;否则 ViewModel 存活期间页面会一直停在
    // PermanentlyDenied 引导页,只能杀进程重开。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermission()
    }

    PermissionGate(
        state = permissionState,
        onRequestResult = viewModel::onRequestResult,
        onOpenAppSettings = {
            context.startActivity(appSettingsIntent(packageName))
        },
        contentWhenGranted = {
            PhotoGrid(items = viewModel.timelineFlow.collectAsLazyPagingItems())
        }
    )
}
