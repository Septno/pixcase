package com.example.pixcase.ui.feature.timeline

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pixcase.R

private const val SKELETON_ITEM_COUNT = 30
private const val SKELETON_COLUMNS = 3
private const val PULSE_MILLIS = 700
private const val PULSE_MIN_ALPHA = 0.25f
private const val PULSE_MAX_ALPHA = 0.6f
private val EMPTY_ICON_SIZE = 56.dp

/**
 * 首屏加载骨架:与照片墙同构的三列网格,方块以透明度脉冲提示加载中。
 *
 * 只在初次加载(已加载数为 0 且 refresh 处于 Loading)时替换整个网格;
 * 翻页 / ContentObserver 失效刷新不打断已展示的照片。
 */
@Composable
fun LoadingSkeletonGrid(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = PULSE_MIN_ALPHA,
        targetValue = PULSE_MAX_ALPHA,
        animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    val color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)

    LazyVerticalGrid(
        columns = GridCells.Fixed(SKELETON_COLUMNS),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CELL_SPACING),
        horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
        verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
    ) {
        items(SKELETON_ITEM_COUNT) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .background(color)
            )
        }
    }
}

/**
 * 空相册状态。注意:隐藏过滤发生在分页之后,若所有照片都被隐藏,
 * 时间线同样呈现为空 —— 对当前阶段这可接受,「隐藏相册」入口在阶段 3 补。
 */
@Composable
fun TimelineEmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.PhotoLibrary,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(EMPTY_ICON_SIZE)
            )
            Text(
                text = stringResource(R.string.empty_timeline_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.empty_timeline_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
