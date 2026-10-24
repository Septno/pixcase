package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pixcase.R

/**
 * 多选模式的顶栏:退出按钮 + 已选计数。
 *
 * 不用 TopAppBar:多选是时间线内的临时状态,自带 statusBars 内嵌的 Surface 即可,
 * 避免与 NavHost 外层 Scaffold 的系统栏内嵌叠加。
 */
@Composable
internal fun SelectionTopBar(count: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }
            Text(
                text = quantityCountLabel(count),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** 已选计数:走 plurals 资源,英文里 one / other 各自成句。 */
@Composable
private fun quantityCountLabel(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.selection_count, count, count)
}

/**
 * 多选模式的底部操作栏。收藏按钮按 [allFavorited] 切换形态:
 * true = 所选全部已收藏,按钮变「取消收藏」;false / null = 任一未收藏,按钮为「收藏」。
 */
@Composable
internal fun SelectionActionBar(
    allFavorited: Boolean?,
    onToggleFavorite: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val favorited = allFavorited == true
            SelectionActionButton(
                icon = if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(if (favorited) R.string.action_unfavorite else R.string.action_favorite),
                onClick = onToggleFavorite
            )
            SelectionActionButton(
                icon = Icons.Outlined.VisibilityOff,
                label = stringResource(R.string.action_hide),
                onClick = onHide
            )
            SelectionActionButton(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
