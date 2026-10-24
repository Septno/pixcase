package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal const val COLUMN_COUNT = 3
internal val CELL_SPACING = 2.dp
private val SELECTION_BORDER_WIDTH = 2.dp
private const val SELECTED_SCRIM_ALPHA = 0.3f

/**
 * 照片墙网格。
 *
 * 日期表头占满整行,作为普通 item 参与滚动 —— Compose 的 [LazyVerticalGrid] 没有吸顶 API,
 * 强行吸顶需要放弃网格改手写三列 Row,而 Paging 的 itemCount 是动态的(末页数量未知),
 * 手写分块必然在翻页边界错位。
 *
 * key 用 mediaId:ContentObserver 触发失效重载后,同一条目仍能复用 Composable
 * 与已解码的缩略图,避免整屏重建。
 *
 * 多选:长按进入并选中;点击回调交由调用方按多选状态分流(切换选中 / 打开查看器),
 * 网格只负责转发原始手势与渲染选中态。
 */
@Composable
fun PhotoGrid(
    items: LazyPagingItems<TimelineItem>,
    selectedIds: Set<Long>,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(COLUMN_COUNT),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(CELL_SPACING),
        horizontalArrangement = Arrangement.spacedBy(CELL_SPACING),
        verticalArrangement = Arrangement.spacedBy(CELL_SPACING)
    ) {
        items(
            count = items.itemCount,
            key = { index -> items.peek(index)?.cacheKey ?: index },
            contentType = { index -> items.peek(index)?.contentType },
            span = { index ->
                if (items.peek(index) is TimelineItem.DateHeader) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            }
        ) { index ->
            when (val item = items[index]) {
                is TimelineItem.DateHeader -> DateHeaderRow(dateHeaderLabel(item.date, today, locale))
                is TimelineItem.Photo -> PhotoCell(
                    photo = item.photo,
                    isSelected = item.photo.id in selectedIds,
                    onPhotoClick = onPhotoClick,
                    onPhotoLongPress = onPhotoLongPress
                )
                // enablePlaceholders = false 时已加载下标不会是占位;未加载的下标
                // LazyGrid 不会请求渲染,这里的 null 分支只为满足类型穷尽。
                null -> Unit
            }
        }
    }
}

@Composable
private fun DateHeaderRow(label: DateHeaderLabel) {
    val text = when (label) {
        DateHeaderLabel.Today -> stringResource(R.string.date_today)
        DateHeaderLabel.Yesterday -> stringResource(R.string.date_yesterday)
        is DateHeaderLabel.Exact -> label.text
    }
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    )
}

@Composable
private fun PhotoCell(
    photo: MediaPhoto,
    isSelected: Boolean,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (Long) -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onPhotoClick(photo.id) },
                onLongClick = { onPhotoLongPress(photo.id) }
            )
    ) {
        AsyncImage(
            // 传 Uri 而不是 OriginalImage:命中缩略图 Fetcher,走系统缩略图。
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            // 用纯色占位而不是留白:缩略图解码前先占住格子,避免首屏布局跳动。
            placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
            error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.matchParentSize()
        )
        if (isSelected) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = SELECTED_SCRIM_ALPHA))
                    .border(SELECTION_BORDER_WIDTH, MaterialTheme.colorScheme.primary)
            )
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(R.string.cd_selected),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )
        }
    }
}
