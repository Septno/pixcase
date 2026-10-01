package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

private const val COLUMN_COUNT = 3
private val CELL_SPACING = 2.dp

/**
 * 照片墙网格。
 *
 * 日期表头占满整行,作为普通 item 参与滚动 —— Compose 的 [LazyVerticalGrid] 没有吸顶 API,
 * 强行吸顶需要放弃网格改手写三列 Row,而 Paging 的 itemCount 是动态的(末页数量未知),
 * 手写分块必然在翻页边界错位。
 *
 * key 用 mediaId:ContentObserver 触发失效重载后,同一条目仍能复用 Composable
 * 与已解码的缩略图,避免整屏重建。
 */
@Composable
fun PhotoGrid(items: LazyPagingItems<TimelineItem>, modifier: Modifier = Modifier) {
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
                is TimelineItem.Photo -> PhotoCell(item.photo)
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
private fun PhotoCell(photo: MediaPhoto) {
    AsyncImage(
        model = photo.uri,
        contentDescription = photo.displayName,
        contentScale = ContentScale.Crop,
        // 用纯色占位而不是留白:缩略图解码前先占住格子,避免首屏布局跳动。
        placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
        error = ColorPainter(MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.aspectRatio(1f)
    )
}
