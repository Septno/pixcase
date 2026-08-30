package com.example.pixcase.core.permission

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pixcase.R

/**
 * 权限引导门。挂在 NavGraph 的 TIMELINE 路由上,根据 [state] 三态渲染:
 *
 * - Checking:居中 CircularProgressIndicator;
 * - NeedsRequest / PermanentlyDenied:Column 居中,展示标题 + 原因 + "Allow" 按钮;
 *   PermanentlyDenied 多一个 "Open settings" 次级按钮,触发 ACTION_APPLICATION_DETAILS_SETTINGS;
 * - Granted:渲染 [contentWhenGranted]。
 *
 * launcher 在 Composable 内创建(per Compose idiom),回调后通知 ViewModel 重新评估状态。
 */
@Composable
fun PermissionGate(
    state: PermissionUiState,
    onRequestResult: (Map<String, Boolean>) -> Unit,
    onOpenAppSettings: () -> Unit = {},
    contentWhenGranted: @Composable () -> Unit
) {
    when (state) {
        is PermissionUiState.Granted -> contentWhenGranted()
        is PermissionUiState.Checking -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        is PermissionUiState.NeedsRequest -> RequestContent(
            missing = state.missing,
            showOpenSettings = false,
            onResult = onRequestResult,
            onOpenSettings = onOpenAppSettings
        )
        is PermissionUiState.PermanentlyDenied -> RequestContent(
            missing = state.missing,
            showOpenSettings = true,
            onResult = onRequestResult,
            onOpenSettings = onOpenAppSettings
        )
    }
}

@Composable
private fun RequestContent(
    missing: List<String>,
    showOpenSettings: Boolean,
    onResult: (Map<String, Boolean>) -> Unit,
    onOpenSettings: () -> Unit
) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> onResult(granted) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.permission_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(
                    if (showOpenSettings) {
                        R.string.permission_permanently_denied_reason
                    } else {
                        R.string.permission_reason
                    }
                ),
                style = MaterialTheme.typography.bodyLarge
            )
            Button(onClick = { launcher.launch(missing.toTypedArray()) }) {
                Text(stringResource(R.string.permission_grant_button))
            }
            if (showOpenSettings) {
                OutlinedButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.permission_open_settings))
                }
            }
        }
    }
}

/** 触发 ACTION_APPLICATION_DETAILS_SETTINGS 跳转到 App 详情页;由 PermissionGate 调用方触发。 */
fun appSettingsIntent(packageName: String): Intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
    data = Uri.fromParts("package", packageName, null)
}
