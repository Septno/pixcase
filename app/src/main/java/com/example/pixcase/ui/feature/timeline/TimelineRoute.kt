package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.pixcase.core.permission.PermissionGate
import com.example.pixcase.core.permission.appSettingsIntent

/**
 * 时间线路由(1.1 阶段最小实现,1.3 替换为 LazyVerticalGrid + sticky headers + 缩略图)。
 *
 * 结构:PermissionGate 三态门 + 授权后的最小 LazyColumn(id + displayName)。
 * 视觉细节(列数 / 缩略图 / sticky date header / 空状态)留 1.3 引入。
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
            val photos = viewModel.pagerFlow.collectAsLazyPagingItems()
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    count = photos.itemCount,
                    key = { index -> photos.peek(index)?.id ?: index }
                ) { index ->
                    val item = photos[index] ?: return@items
                    Text("${item.id}: ${item.displayName}")
                }
            }
        }
    )
}
