package com.example.pixcase.ui.feature.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.imageContentUri
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.ui.feature.timeline.SelectionActionButton
import com.example.pixcase.ui.feature.timeline.SelectionTopBar

internal const val DETAIL_COLUMN_COUNT = 3
private val DETAIL_CELL_SPACING = 2.dp
private val SELECTION_BORDER_WIDTH = 2.dp
private const val SELECTED_SCRIM_ALPHA = 0.3f

/**
 * 自定义相册详情页:相册内照片网格,点照片进全屏查看器。
 *
 * 多选语义与时间线一致:长按进入,选中集非空即多选态,选空自动退出;多选时 BACK 只退出
 * 多选;操作 = 移出相册 / 设为封面(选中 1 张时)。移出仅解除关联,原图不动。
 * 查看器的翻页上下文仍是时间线的分页窗口(查看器复用时间线 ViewModel,见 NavGraph 注释)
 * —— 这是当前已知限制,相册内连续翻页属后续增强。
 */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onPhotoClick: (Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val albumName by viewModel.albumName.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is AlbumDetailEvent.Message ->
                    snackbarHostState.showSnackbar(context.getString(event.resId, event.count))
            }
        }
    }
    // 多选激活时 BACK 只退出多选,不退出详情页(同时间线)。
    BackHandler(enabled = selectedIds.isNotEmpty()) { viewModel.clearSelection() }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (selectedIds.isEmpty()) {
                AlbumDetailTopBar(name = albumName, count = photos.size, onBack = onBack)
            } else {
                SelectionTopBar(count = selectedIds.size, onClose = viewModel::clearSelection)
            }
            AlbumDetailContent(
                photos = photos,
                selectedIds = selectedIds,
                onPhotoClick = { photoId ->
                    if (selectedIds.isEmpty()) onPhotoClick(photoId) else viewModel.togglePhotoSelection(photoId)
                },
                onPhotoLongPress = viewModel::selectPhoto,
                modifier = Modifier.weight(1f)
            )
            if (selectedIds.isNotEmpty()) {
                AlbumSelectionActionBar(
                    onRemove = viewModel::removeSelection,
                    onSetCover = viewModel::setCoverForSelection
                )
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 正文两态:空相册提示(照片移空后可见)/ 照片网格。 */
@Composable
private fun AlbumDetailContent(
    photos: List<MediaPhoto>,
    selectedIds: Set<Long>,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (photos.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.albums_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(DETAIL_COLUMN_COUNT),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(DETAIL_CELL_SPACING),
            horizontalArrangement = Arrangement.spacedBy(DETAIL_CELL_SPACING),
            verticalArrangement = Arrangement.spacedBy(DETAIL_CELL_SPACING)
        ) {
            items(photos.size, key = { photos[it].id }) { index ->
                val photo = photos[index]
                DetailPhotoCell(
                    photo = photo,
                    isSelected = photo.id in selectedIds,
                    onPhotoClick = onPhotoClick,
                    onPhotoLongPress = { onPhotoLongPress(photo.id) }
                )
            }
        }
    }
}

/** 详情网格的照片格子:缩略图 + 点击;支持多选态(时间线网格同款视觉)。 */
@Composable
internal fun DetailPhotoCell(
    photo: MediaPhoto,
    isSelected: Boolean = false,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = { onPhotoClick(photo.id) },
                onLongClick = onPhotoLongPress
            )
    ) {
        AsyncImage(
            model = imageContentUri(photo.id),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            // 纯色占位:缩略图解码前先占住格子,避免首屏布局跳动(同时间线)。
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

/** 相册详情页多选操作栏:移出相册 / 设为封面。 */
@Composable
private fun AlbumSelectionActionBar(onRemove: () -> Unit, onSetCover: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SelectionActionButton(
                icon = Icons.Outlined.RemoveCircleOutline,
                label = stringResource(R.string.action_remove_from_album),
                onClick = onRemove
            )
            SelectionActionButton(
                icon = Icons.Outlined.Image,
                label = stringResource(R.string.action_set_cover),
                onClick = onSetCover
            )
        }
    }
}

/** 详情页顶栏(返回 + 名称 + 条数),自定义相册与智能相册详情共用。 */
@Composable
internal fun AlbumDetailTopBar(name: String, count: Int, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.viewer_back)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = quantityCountLabel(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 相册内照片计数:走 plurals 资源。 */
@Composable
internal fun quantityCountLabel(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.albums_count, count, count)
}
