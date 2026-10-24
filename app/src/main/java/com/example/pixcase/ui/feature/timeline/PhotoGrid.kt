package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
 *
 * 捏合变列数:双指捏合按 [ZOOM_STEP] 分步改变列数,列数状态在 ViewModel 持久化;
 * 变化时把当前屏第一张照片作为视觉锚点重新定位(见 [pinchColumnGesture] 与锚点 LaunchedEffect)。
 */
@Composable
fun PhotoGrid(
    items: LazyPagingItems<TimelineItem>,
    selectedIds: Set<Long>,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val screenWidthDp = LocalConfiguration.current.screenWidthDp

    // 持久化的列数可能来自更宽的屏(平板上捏过),显示时按当前宽度再夹一次。
    val effectiveColumns = clampColumns(columnCount, screenWidthDp)
    val gridState = rememberLazyGridState()

    // 视觉锚点:列数变化前后,扁平列表的条目顺序不变(只是每行列数不同),
    // 记下变化前第一张可见照片的下标,重排后 scroll 回去,照片不会被"滚走"。
    var anchorIndex by remember { mutableIntStateOf(-1) }

    // pointerInput 不能 key 在列数上:列数变化会重启手势检测,把进行中的捏合掐断。
    // 用 rememberUpdatedState 让手势回调永远读到最新列数。
    val currentColumns by rememberUpdatedState(effectiveColumns)
    val pinchModifier = Modifier.pinchColumnGesture(
        getCurrentColumns = { currentColumns },
        onColumnCountChange = onColumnCountChange,
        captureAnchor = {
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull { info -> items.peek(info.index) is TimelineItem.Photo }
                ?.index
                ?.let { anchorIndex = it }
        }
    )

    LaunchedEffect(columnCount) {
        if (anchorIndex >= 0) {
            gridState.scrollToItem(anchorIndex)
            anchorIndex = -1
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(effectiveColumns),
        modifier = modifier
            .fillMaxSize()
            .then(pinchModifier),
        state = gridState,
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

/**
 * 双指捏合变列数的手势检测。
 *
 * 监听挂在 [PointerEventPass.Initial](隧道阶段,先于网格内部 scroll 的 Main 阶段):
 * 捏合期间(≥2 指按下)消费掉位置变化,网格不会同时被单指滚动带跑;
 * 单指触摸不消费,滚动、点击、长按全部照旧。
 *
 * 每次事件的 zoom 经 [accumulateZoom] 累积,跨过一档 [ZOOM_STEP] 才提交一次列数变化
 * —— 这是缩略图重解码的节流阀:Coil 按格子尺寸重新请求,连续微小抖动不再触发。
 * 不 key 在列数上(pointerInput(Unit)):列数变化若重启手势检测,会把进行中的捏合掐断。
 */
private fun Modifier.pinchColumnGesture(
    getCurrentColumns: () -> Int,
    onColumnCountChange: (Int) -> Unit,
    captureAnchor: () -> Unit
): Modifier = pointerInput(Unit) {
    var accumulator = 1f
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressedCount = event.changes.count { it.pressed }
            if (pressedCount >= 2) {
                val (newAccumulator, steps) = accumulateZoom(accumulator, event.calculateZoom())
                accumulator = newAccumulator
                if (steps != 0) {
                    captureAnchor()
                    val next = (getCurrentColumns() + steps).coerceAtLeast(MIN_COLUMNS)
                    if (next != getCurrentColumns()) onColumnCountChange(next)
                }
                // 只在捏合时消费,位置变化不流入网格滚动。
                event.changes.forEach { change ->
                    if (change.positionChanged()) change.consume()
                }
            } else {
                // 捏完剩单指:立即停止消费、累积器归位,单指自然回到滚动语义;
                // 全部抬起则手势结束,同样归位,避免下次捏合继承残余幅度。
                accumulator = 1f
                if (pressedCount == 0) break
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
