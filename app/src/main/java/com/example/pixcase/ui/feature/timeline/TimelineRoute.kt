package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.pixcase.core.permission.PermissionGate
import com.example.pixcase.core.permission.appSettingsIntent

/**
 * 时间线路由(1.1 阶段最小实现,1.3 替换为 LazyVerticalGrid + sticky headers + 缩略图)。
 *
 * 结构:PermissionGate 三态门 + 授权后的最小 LazyColumn(id + displayName)。
 * 视觉细节(列数 / 缩略图 / sticky date header / 空状态)留 1.3 引入。
 *
 * 使用 collectAsState 而非 collectAsStateWithLifecycle,因为本项目尚未引入
 * androidx.lifecycle:lifecycle-runtime-compose;1.3+ 评估是否升级。
 */
@Composable
fun TimelineRoute(viewModel: TimelineViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsState()
    val packageName = context.packageName

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
