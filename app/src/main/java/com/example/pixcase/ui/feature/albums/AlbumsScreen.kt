package com.example.pixcase.ui.feature.albums

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.MediaSummary
import com.example.pixcase.data.mediastore.imageContentUri

/** 智能相册标识:详情路由与数据源分流按它决定。 */
const val SMART_FAVORITES = "favorites"
const val SMART_SCREENSHOTS = "screenshots"

/** 智能相册卡片是横卡,宽高比固定 1.6。 */
private const val SMART_CARD_ASPECT_RATIO = 1.6f

/**
 * 相册 Tab:智能相册(收藏 / 截图)+ 自定义相册卡片 + 新建入口。
 *
 * 自定义相册封面 = coverMediaId(加入照片时自动补第一张,或手动指定),空封面用图标占位;
 * 卡片点击进详情页,卡片右上角菜单提供 重命名 / 删除。
 * 智能相册封面 = 条件筛选的最近一张,计数来自 MediaStore 概览查询;点击进智能相册详情页。
 */
@Composable
fun AlbumsScreen(
    onAlbumClick: (Long) -> Unit,
    onSmartAlbumClick: (String) -> Unit,
    viewModel: AlbumsViewModel = hiltViewModel()
) {
    val albums by viewModel.userAlbums.collectAsStateWithLifecycle()
    val favoritesSummary by viewModel.favoritesSummary.collectAsStateWithLifecycle()
    val screenshotsSummary by viewModel.screenshotsSummary.collectAsStateWithLifecycle()
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AlbumCard?>(null) }
    var deleteTarget by remember { mutableStateOf<AlbumCard?>(null) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.albums_create)) }
            )
        }
    ) { padding ->
        AlbumsGrid(
            albums = albums,
            favoritesSummary = favoritesSummary,
            screenshotsSummary = screenshotsSummary,
            onAlbumClick = onAlbumClick,
            onSmartAlbumClick = onSmartAlbumClick,
            onRename = { renameTarget = it },
            onDelete = { deleteTarget = it },
            modifier = Modifier.padding(padding)
        )
    }

    AlbumsDialogs(
        showCreateDialog = showCreateDialog,
        renameTarget = renameTarget,
        deleteTarget = deleteTarget,
        onCreate = { name ->
            showCreateDialog = false
            viewModel.createAlbum(name) { }
        },
        onRename = { albumId, name ->
            renameTarget = null
            viewModel.renameAlbum(albumId, name) { }
        },
        onDelete = { albumId ->
            deleteTarget = null
            viewModel.deleteAlbum(albumId)
        },
        onDismissDialogs = {
            showCreateDialog = false
            renameTarget = null
            deleteTarget = null
        }
    )
}

/** 新建 / 重命名 / 删除三种相册对话框;确认即关自身,回调只承载业务动作。 */
@Composable
private fun AlbumsDialogs(
    showCreateDialog: Boolean,
    renameTarget: AlbumCard?,
    deleteTarget: AlbumCard?,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit,
    onDismissDialogs: () -> Unit
) {
    if (showCreateDialog) {
        NameInputDialog(
            title = stringResource(R.string.albums_create),
            initialValue = "",
            onConfirm = onCreate,
            onDismiss = onDismissDialogs
        )
    }
    renameTarget?.let { album ->
        NameInputDialog(
            title = stringResource(R.string.albums_rename),
            initialValue = album.name,
            onConfirm = { name -> onRename(album.id, name) },
            onDismiss = onDismissDialogs
        )
    }
    deleteTarget?.let { album ->
        AlertDialog(
            onDismissRequest = onDismissDialogs,
            title = { Text(stringResource(R.string.albums_delete_confirm_title)) },
            text = { Text(stringResource(R.string.albums_delete_confirm_text, album.name)) },
            confirmButton = {
                TextButton(onClick = { onDelete(album.id) }) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDialogs) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

/** 相册网格:智能相册卡在前,自定义相册卡片在后。 */
@Composable
private fun AlbumsGrid(
    albums: List<AlbumCard>,
    favoritesSummary: MediaSummary?,
    screenshotsSummary: MediaSummary?,
    onAlbumClick: (Long) -> Unit,
    onSmartAlbumClick: (String) -> Unit,
    onRename: (AlbumCard) -> Unit,
    onDelete: (AlbumCard) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "smart-favorites") {
            SmartAlbumCard(
                icon = Icons.Filled.Favorite,
                label = stringResource(R.string.albums_smart_favorites),
                summary = favoritesSummary,
                onClick = { onSmartAlbumClick(SMART_FAVORITES) }
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            item(key = "smart-screenshots") {
                SmartAlbumCard(
                    icon = Icons.Outlined.Screenshot,
                    label = stringResource(R.string.albums_smart_screenshots),
                    summary = screenshotsSummary,
                    onClick = { onSmartAlbumClick(SMART_SCREENSHOTS) }
                )
            }
        }
        items(albums.size, key = { albums[it].id }) { index ->
            val album = albums[index]
            AlbumCardView(
                album = album,
                onClick = { onAlbumClick(album.id) },
                onRename = { onRename(album) },
                onDelete = { onDelete(album) }
            )
        }
    }
}

/** 智能相册卡片:封面(筛选结果最近一张,空则图标)+ 名称 + 条数。 */
@Composable
private fun SmartAlbumCard(icon: ImageVector, label: String, summary: MediaSummary?, onClick: () -> Unit) {
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(SMART_CARD_ASPECT_RATIO)
        ) {
            val coverMediaId = summary?.latestMediaId
            if (coverMediaId != null) {
                AsyncImage(
                    model = imageContentUri(coverMediaId),
                    contentDescription = label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = quantityCountLabel(summary?.count ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlbumCardView(album: AlbumCard, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
        ) {
            if (album.coverMediaId != null) {
                AsyncImage(
                    model = imageContentUri(album.coverMediaId),
                    contentDescription = album.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PhotoAlbum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                TextButton(onClick = { menuOpen = true }) {
                    Text(stringResource(R.string.albums_menu), style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.albums_rename)) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_delete)) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }
        Column(modifier = Modifier.padding(8.dp)) {
            Text(text = album.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                text = quantityCountLabel(album.photoCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 新建 / 重命名共用的单行文本输入对话框。 */
@Composable
internal fun NameInputDialog(title: String, initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
                Text(stringResource(R.string.action_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/** 相册内照片计数:走 plurals 资源。 */
@Composable
private fun quantityCountLabel(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.albums_count, count, count)
}
