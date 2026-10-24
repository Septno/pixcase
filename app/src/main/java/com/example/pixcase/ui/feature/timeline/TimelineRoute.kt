package com.example.pixcase.ui.feature.timeline

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.pixcase.R
import com.example.pixcase.core.permission.PermissionGate
import com.example.pixcase.core.permission.appSettingsIntent

/**
 * 时间线路由。
 *
 * 结构:PermissionGate 三态门 + 授权后的 [TimelineScreen](照片墙 + 加载状态 + 多选操作)。
 */
@Composable
fun TimelineRoute(onPhotoClick: (Long) -> Unit, viewModel: TimelineViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val permissionState by viewModel.permissionState.collectAsStateWithLifecycle()
    val packageName = context.packageName

    // 用户点 "Open settings" 跳去系统设置授权后返回,权限弹窗回调不会再触发,
    // 必须借 ON_RESUME 重新检查权限;否则 ViewModel 存活期间页面会一直停在
    // PermanentlyDenied 引导页,只能杀进程重开。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.refreshPermission()
    }

    PermissionGate(
        state = permissionState,
        onRequestResult = viewModel::onRequestResult,
        onOpenAppSettings = {
            context.startActivity(appSettingsIntent(packageName))
        },
        contentWhenGranted = {
            TimelineScreen(viewModel = viewModel, onOpenViewer = onPhotoClick)
        }
    )
}

@Composable
private fun TimelineScreen(viewModel: TimelineViewModel, onOpenViewer: (Long) -> Unit) {
    val lazyItems = viewModel.timelineFlow.collectAsLazyPagingItems()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val allFavorited by viewModel.selectionAllFavorited.collectAsStateWithLifecycle()
    val columnCount by viewModel.columnCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val dialogs = remember { TimelineDialogsState() }
    TimelineEventsEffect(
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
        onRequestDeleteDialog = { dialogs.showDeleteDialog = true },
        onOpenRenameDialog = { baseName -> dialogs.renameBaseName = baseName },
        onOpenTagDialog = { payload -> dialogs.tagDialogPayload = payload },
        onOpenAlbumPicker = { payload -> dialogs.albumPickerPayload = payload }
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SelectionTopBarIfActive(selectedIds = selectedIds, onClose = viewModel::clearSelection)
            Box(modifier = Modifier.weight(1f)) {
                TimelineContent(
                    items = lazyItems,
                    selectedIds = selectedIds,
                    columnCount = columnCount,
                    onColumnCountChange = viewModel::setColumnCount,
                    onPhotoClick = { photoId ->
                        if (selectedIds.isEmpty()) onOpenViewer(photoId) else viewModel.togglePhotoSelection(photoId)
                    },
                    onPhotoLongPress = viewModel::selectPhoto
                )
            }
            SelectionActionBarIfActive(
                selectedIds = selectedIds,
                allFavorited = allFavorited,
                onToggleFavorite = viewModel::toggleFavoriteForSelection,
                onAddTag = viewModel::requestTagSelection,
                onAddToAlbum = viewModel::requestAddToAlbum,
                onShare = viewModel::shareSelection,
                onRename = viewModel::requestRename,
                onSetWallpaper = viewModel::setWallpaperForSelection,
                onHide = viewModel::hideSelection,
                onDelete = viewModel::deleteSelection
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    TimelineDialogs(
        state = dialogs,
        deleteCount = selectedIds.size,
        onDeleteConfirmed = viewModel::deleteSelectionConfirmed,
        onRenameConfirmed = viewModel::renameSelection,
        onTagConfirmed = viewModel::applyTags,
        onTagCreate = viewModel::createTagInDialog,
        onAlbumPicked = viewModel::addSelectionToAlbum,
        onAlbumCreate = viewModel::createAlbumInDialog
    )
}

/**
 * 多选操作触发的四种对话框(永久删除确认 / 重命名 / 打标签 / 加入相册)的可见性状态。
 * 标签与相册对话框载荷含实体列表,不走自动 Saveable:转屏即关,这些是短操作可接受。
 */
@Stable
private class TimelineDialogsState {
    var showDeleteDialog by mutableStateOf(false)
    var renameBaseName by mutableStateOf<String?>(null)
    var tagDialogPayload by mutableStateOf<TimelineEvent.OpenTagDialog?>(null)
    var albumPickerPayload by mutableStateOf<TimelineEvent.OpenAlbumPicker?>(null)
}

/** 多选激活时显示顶栏(退出按钮 + 已选计数),否则不占任何空间。 */
@Composable
private fun SelectionTopBarIfActive(selectedIds: Set<Long>, onClose: () -> Unit) {
    if (selectedIds.isNotEmpty()) {
        SelectionTopBar(count = selectedIds.size, onClose = onClose)
    }
}

/**
 * 多选激活时显示底部操作栏,否则不占任何空间 —— 网格缩小让位,而不是悬浮遮挡照片。
 * weight 调整必须在真正的 ColumnScope 里做,故拆成两个 IfActive 条件组件夹住正文。
 */
@Composable
private fun SelectionActionBarIfActive(
    selectedIds: Set<Long>,
    allFavorited: Boolean?,
    onToggleFavorite: () -> Unit,
    onAddTag: () -> Unit,
    onAddToAlbum: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onSetWallpaper: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    if (selectedIds.isNotEmpty()) {
        SelectionActionBar(
            allFavorited = allFavorited,
            onToggleFavorite = onToggleFavorite,
            onAddTag = onAddTag,
            onAddToAlbum = onAddToAlbum,
            onShare = onShare,
            onRename = onRename,
            onSetWallpaper = onSetWallpaper,
            onHide = onHide,
            onDelete = onDelete
        )
    }
}

/** 确认框 / 输入框 / 打标签 / 加入相册对话框集中渲染,均由事件或操作栏触发。 */
@Composable
private fun TimelineDialogs(
    state: TimelineDialogsState,
    deleteCount: Int,
    onDeleteConfirmed: () -> Unit,
    onRenameConfirmed: (String) -> Unit,
    onTagConfirmed: (Set<Long>) -> Unit,
    onTagCreate: (String) -> Unit,
    onAlbumPicked: (Long) -> Unit,
    onAlbumCreate: (String) -> Unit
) {
    // 对话框自行负责「确认 / 关闭 = 关掉自己」,回调只承载业务动作
    if (state.showDeleteDialog) {
        PermanentDeleteDialog(
            count = deleteCount,
            onConfirm = {
                state.showDeleteDialog = false
                onDeleteConfirmed()
            },
            onDismiss = { state.showDeleteDialog = false }
        )
    }
    state.renameBaseName?.let { baseName ->
        RenameDialog(
            currentBaseName = baseName,
            onConfirm = { input ->
                state.renameBaseName = null
                onRenameConfirmed(input)
            },
            onDismiss = { state.renameBaseName = null }
        )
    }
    state.tagDialogPayload?.let { payload ->
        TagDialog(
            tags = payload.tags,
            checkedTagIds = payload.checkedTagIds,
            onConfirm = { finalChecked ->
                state.tagDialogPayload = null
                onTagConfirmed(finalChecked)
            },
            onCreateTag = onTagCreate,
            onDismiss = { state.tagDialogPayload = null }
        )
    }
    state.albumPickerPayload?.let { payload ->
        AlbumPickerDialog(
            albums = payload.albums,
            onPick = onAlbumPicked,
            onCreate = onAlbumCreate,
            onDismiss = { state.albumPickerPayload = null }
        )
    }
}

/**
 * 网格内容的三态切换:首屏骨架 → 空相册 → 下拉刷新包裹的照片墙。
 *
 * 骨架 / 空态只在「首屏尚未有任何已加载条目」时替换整个网格;
 * 翻页与 ContentObserver 失效刷新不打断已展示的照片。
 */
@Composable
private fun TimelineContent(
    items: LazyPagingItems<TimelineItem>,
    selectedIds: Set<Long>,
    columnCount: Int,
    onColumnCountChange: (Int) -> Unit,
    onPhotoClick: (Long) -> Unit,
    onPhotoLongPress: (Long) -> Unit
) {
    when {
        items.loadState.refresh is LoadState.Loading && items.itemCount == 0 -> LoadingSkeletonGrid()
        items.loadState.refresh is LoadState.NotLoading && items.itemCount == 0 -> TimelineEmptyState()
        else -> PullToRefreshBox(
            isRefreshing = items.loadState.refresh is LoadState.Loading,
            onRefresh = { items.refresh() }
        ) {
            PhotoGrid(
                items = items,
                selectedIds = selectedIds,
                columnCount = columnCount,
                onColumnCountChange = onColumnCountChange,
                onPhotoClick = onPhotoClick,
                onPhotoLongPress = onPhotoLongPress
            )
        }
    }
}

/**
 * 一次性事件接线:启动系统 PendingIntent(删除确认 / 收藏同步 / 写授权)与普通 Intent
 * (分享 chooser / 壁纸裁剪)、弹出对话框、Snackbar 反馈。
 * 启动动作必须发生在 Activity 侧,故集中在 Composable 处理。
 */
@Composable
private fun TimelineEventsEffect(
    viewModel: TimelineViewModel,
    snackbarHostState: SnackbarHostState,
    onRequestDeleteDialog: () -> Unit,
    onOpenRenameDialog: (String) -> Unit,
    onOpenTagDialog: (TimelineEvent.OpenTagDialog) -> Unit,
    onOpenAlbumPicker: (TimelineEvent.OpenAlbumPicker) -> Unit
) {
    val context = LocalContext.current
    // 多选激活时 BACK 只退出多选,不退出页面(时间线是起始目的地,默认 BACK 会直接结束 Activity)。
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    BackHandler(enabled = selectedIds.isNotEmpty()) { viewModel.clearSelection() }
    val deleteConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onSystemDeleteApproved()
    }
    // 收藏同步不弹系统界面,结果不处理:Room 已先写入,同步是尽力而为。
    val favoriteSyncLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { }
    // 重命名的写授权:同意后重试直接更新;拒绝则丢弃挂起的重命名,现场不变。
    val writeConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onWriteConsentApproved()
        } else {
            viewModel.onWriteConsentDenied()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TimelineEvent.LaunchDeleteConsent ->
                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                is TimelineEvent.LaunchWriteConsent ->
                    writeConsentLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                is TimelineEvent.LaunchFavoriteSync ->
                    favoriteSyncLauncher.launch(IntentSenderRequest.Builder(event.sender).build())
                is TimelineEvent.ConfirmPermanentDelete -> onRequestDeleteDialog()
                is TimelineEvent.SharePhotos ->
                    launchPhotoShare(context, event.uris) { messageRes ->
                        snackbarHostState.showSnackbar(context.getString(messageRes))
                    }
                is TimelineEvent.LaunchSetWallpaper ->
                    launchWallpaperPicker(context, event.intent) { messageRes ->
                        snackbarHostState.showSnackbar(context.getString(messageRes))
                    }
                is TimelineEvent.OpenRenameDialog -> onOpenRenameDialog(event.currentBaseName)
                is TimelineEvent.OpenTagDialog -> onOpenTagDialog(event)
                is TimelineEvent.OpenAlbumPicker -> onOpenAlbumPicker(event)
                is TimelineEvent.Message ->
                    snackbarHostState.showSnackbar(context.getString(event.resId, event.count))
            }
        }
    }
}

/** 多图分享:系统 chooser;设备上没有可接收的应用时提示。 */
private suspend fun launchPhotoShare(context: Context, uris: List<Uri>, showErrorMessage: suspend (Int) -> Unit) {
    val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "image/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(shareIntent, null))
    } catch (ignored: ActivityNotFoundException) {
        showErrorMessage(R.string.msg_no_share_app)
    }
}

/** 启动系统「裁剪并设为壁纸」;无处理组件时提示。 */
private suspend fun launchWallpaperPicker(context: Context, intent: Intent, showErrorMessage: suspend (Int) -> Unit) {
    try {
        context.startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
        showErrorMessage(R.string.msg_wallpaper_failed)
    }
}

/**
 * API 29 及以下的删除确认框:没有系统回收站,删除不可撤销,必须显式告知。
 * API 30+ 不走这里 —— 系统自带的确认框同意后照片进回收站。
 */
@Composable
private fun PermanentDeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val resources = LocalContext.current.resources
    val message = resources.getQuantityString(R.plurals.delete_confirm_text, count, count)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_confirm_title)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.delete_confirm_ok),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
