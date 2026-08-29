package com.example.pixcase.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pixcase.ui.theme.PixcaseTheme

/**
 * 阶段 0 占位界面:仅显示路由名。
 * 后续阶段由各 feature package 替换为真实屏幕。
 */
@Composable
fun PlaceholderScreen(route: String) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Stage 0 placeholder",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = route,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Preview
@Composable
private fun PlaceholderScreenPreview() {
    PixcaseTheme {
        PlaceholderScreen("timeline")
    }
}