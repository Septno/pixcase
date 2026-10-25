package com.example.pixcase.ui.feature.quicksort

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.imageContentUri

/** Dock 目标圆标直径。 */
private val DOCK_ICON_SIZE = 52.dp

/** Dock 与屏幕左右边缘的留白。 */
private val DOCK_CONTENT_PADDING = 16.dp

/** 悬停高亮时的放大比例与描边宽度。 */
private const val DOCK_HIGHLIGHT_SCALE = 1.1f
private val DOCK_HIGHLIGHT_BORDER = 3.dp

/**
 * 目标相册 Dock(计划 § 3.5.5):底部横排,只放 session 的目标相册(智能相册不在 Room,
 * 天然不出现),末尾 ➕ 进目标管理界面。拖拽中的卡片与某条目重叠时该条目高亮放大。
 *
 * [onItemBoundsChanged]:条目把自己的屏幕坐标矩形报给上层(卡片手势做重叠检测);
 * [highlightedId] 非空时对应条目高亮发光。
 */
@Composable
internal fun AlbumDock(
    albums: List<DockAlbum>,
    highlightedId: Long?,
    onItemBoundsChanged: (Long, Rect) -> Unit,
    onAddTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = DOCK_CONTENT_PADDING, vertical = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            albums.forEach { album ->
                DockAlbumItem(
                    album = album,
                    highlighted = album.id == highlightedId,
                    onBoundsChanged = { rect -> onItemBoundsChanged(album.id, rect) }
                )
            }
            IconButton(onClick = onAddTarget) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = stringResource(R.string.quicksort_dock_add)
                )
            }
        }
    }
}

/** 单个目标条目:封面圆标(缺失用相册图标)+ 名称;高亮时描边 + 放大。 */
@Composable
private fun DockAlbumItem(album: DockAlbum, highlighted: Boolean, onBoundsChanged: (Rect) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .graphicsLayer {
                scaleX = if (highlighted) DOCK_HIGHLIGHT_SCALE else 1f
                scaleY = if (highlighted) DOCK_HIGHLIGHT_SCALE else 1f
            }
    ) {
        Box(
            modifier = Modifier
                .size(DOCK_ICON_SIZE)
                .clip(CircleShape)
                .then(
                    if (highlighted) {
                        Modifier.border(DOCK_HIGHLIGHT_BORDER, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier
                    }
                )
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (album.coverMediaId != null) {
                AsyncImage(
                    model = imageContentUri(album.coverMediaId),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(DOCK_ICON_SIZE)
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoAlbum,
                    contentDescription = album.name,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
