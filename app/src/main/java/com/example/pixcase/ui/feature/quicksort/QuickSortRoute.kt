package com.example.pixcase.ui.feature.quicksort

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.model.MediaPhoto

/**
 * 快速整理路由:进入分流(Loading → NeedsChoice / NeedsConfig)→ Active 整理界面。
 * 配置阶段(选择/继续)只是 Sheet / Dialog,Active 才是真正的整理界面;取消配置即返回详情页。
 */
@Composable
fun QuickSortRoute(onBack: () -> Unit, onOpenTargets: () -> Unit, viewModel: QuickSortViewModel = hiltViewModel()) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val albumName by viewModel.albumName.collectAsStateWithLifecycle()
    val candidateAlbums by viewModel.candidateAlbums.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val pendingPhotos by viewModel.pendingPhotos.collectAsStateWithLifecycle()
    val dockAlbums by viewModel.dockAlbums.collectAsStateWithLifecycle()
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showFallback by remember { mutableStateOf(false) }
    // Dock 拖放:条目坐标矩形(手势线程外维护,主线程读写无并发)与当前悬停目标
    val dockRects = remember { mutableMapOf<Long, Rect>() }
    var hoverTarget by remember { mutableStateOf<Long?>(null) }

    when (stage) {
        QuickSortStage.Loading -> Unit
        QuickSortStage.NeedsChoice -> ResumeSheet(
            viewModel = viewModel,
            onDismiss = onBack
        )
        QuickSortStage.NeedsConfig -> SetupDialog(
            candidates = candidateAlbums,
            onStart = viewModel::startSession,
            onDismiss = onBack
        )
        QuickSortStage.Active -> QuickSortActive(
            viewModel = viewModel,
            albumName = albumName,
            session = session,
            pendingPhotos = pendingPhotos,
            dockAlbums = dockAlbums,
            onBack = onBack,
            onOpenTargets = onOpenTargets
        )
    }
}

/** Active 阶段:整理界面 + ⟲ 确认 / 兜底 Sheet + Dock 拖放状态。 */
@Composable
private fun QuickSortActive(
    viewModel: QuickSortViewModel,
    albumName: String,
    session: com.example.pixcase.data.db.entity.QuickSortSessionEntity?,
    pendingPhotos: List<MediaPhoto>,
    dockAlbums: List<DockAlbum>,
    onBack: () -> Unit,
    onOpenTargets: () -> Unit
) {
    var showRestartConfirm by remember { mutableStateOf(false) }
    var showFallback by remember { mutableStateOf(false) }
    // Dock 拖放:条目坐标矩形与当前悬停目标(主线程读写,无并发)
    val dockRects = remember { mutableMapOf<Long, Rect>() }
    var hoverTarget by remember { mutableStateOf<Long?>(null) }

    QuickSortScreen(
        albumName = albumName,
        processedCount = session?.processedCount ?: 0,
        totalCount = session?.totalCount ?: 0,
        pendingPhotos = pendingPhotos,
        dockAlbums = dockAlbums,
        hoverTarget = hoverTarget,
        dockRects = dockRects,
        onHoverTargetChange = { hoverTarget = it },
        onExit = { currentIndex -> viewModel.exit(currentIndex, onBack) },
        onRequestRestart = { showRestartConfirm = true },
        onKeep = viewModel::keepCurrent,
        onFallback = { showFallback = true },
        onMove = viewModel::moveCurrentTo,
        onAddTarget = onOpenTargets
    )
    QuickSortActiveDialogs(
        showRestartConfirm = showRestartConfirm,
        showFallback = showFallback,
        processedCount = session?.processedCount ?: 0,
        fallbackPhoto = pendingPhotos.firstOrNull(),
        onRestartConfirmed = {
            showRestartConfirm = false
            viewModel.restartSession()
        },
        onRestartDismissed = { showRestartConfirm = false },
        onFallbackKeep = {
            showFallback = false
            viewModel.keepCurrent()
        },
        onFallbackSkip = {
            showFallback = false
            viewModel.skipCurrent()
        },
        onFallbackDismissed = { showFallback = false }
    )
}

/** Active 阶段的两个浮层:⟲ 二次确认 与 兜底 Sheet。 */
@Composable
private fun QuickSortActiveDialogs(
    showRestartConfirm: Boolean,
    showFallback: Boolean,
    processedCount: Int,
    fallbackPhoto: MediaPhoto?,
    onRestartConfirmed: () -> Unit,
    onRestartDismissed: () -> Unit,
    onFallbackKeep: () -> Unit,
    onFallbackSkip: () -> Unit,
    onFallbackDismissed: () -> Unit
) {
    if (showRestartConfirm) {
        RestartConfirmDialog(
            processedCount = processedCount,
            onConfirm = onRestartConfirmed,
            onDismiss = onRestartDismissed
        )
    }
    if (showFallback) {
        FallbackSheet(
            photo = fallbackPhoto,
            onKeep = onFallbackKeep,
            onSkip = onFallbackSkip,
            onDismiss = onFallbackDismissed
        )
    }
}

/**
 * 整理界面:顶栏 = ←(退出自动保存)+「相册名 · 快速整理」+ 进度 N/M + ⟲(二次确认);
 * 正文 = 卡片堆叠(队列排空后显示完成态)+ 底部目标相册 Dock。
 */
@Composable
private fun QuickSortScreen(
    albumName: String,
    processedCount: Int,
    totalCount: Int,
    pendingPhotos: List<MediaPhoto>,
    dockAlbums: List<DockAlbum>,
    hoverTarget: Long?,
    dockRects: MutableMap<Long, Rect>,
    onHoverTargetChange: (Long?) -> Unit,
    onExit: (Int) -> Unit,
    onRequestRestart: () -> Unit,
    onKeep: () -> Unit,
    onFallback: () -> Unit,
    onMove: (Long) -> Unit,
    onAddTarget: () -> Unit
) {
    // 退出保存的「当前位置」= 已处理张数(队列随决策收缩,下标语义等价于进度)
    val currentIndex = processedCount
    BackHandler { onExit(currentIndex) }

    Column(modifier = Modifier.fillMaxSize()) {
        QuickSortTopBar(
            albumName = albumName,
            processedCount = processedCount,
            totalCount = totalCount,
            onBack = { onExit(currentIndex) },
            onRestart = onRequestRestart
        )
        if (pendingPhotos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.quicksort_done),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            PhotoCardStack(
                photos = pendingPhotos,
                onKeep = onKeep,
                onFallback = onFallback,
                onMove = onMove,
                dropTargets = { dockRects.toMap() },
                onHoverChange = onHoverTargetChange,
                modifier = Modifier.weight(1f)
            )
        }
        AlbumDock(
            albums = dockAlbums,
            highlightedId = hoverTarget,
            onItemBoundsChanged = { id, rect -> dockRects[id] = rect },
            onAddTarget = onAddTarget
        )
    }
}

/** 顶栏:返回(退出自动保存)+ 标题 + 进度 N/M + ⟲(重启,入口处必弹二次确认)。 */
@Composable
private fun QuickSortTopBar(
    albumName: String,
    processedCount: Int,
    totalCount: Int,
    onBack: () -> Unit,
    onRestart: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.viewer_back)
                )
            }
            Text(
                text = stringResource(R.string.quicksort_title_with_album, albumName),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$processedCount / $totalCount",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            IconButton(onClick = onRestart) {
                Icon(
                    imageVector = Icons.Outlined.Replay,
                    contentDescription = stringResource(R.string.quicksort_cd_restart)
                )
            }
        }
    }
}

/** ⟲ 重启二次确认(计划 § 3.5.2 文案;主按钮 destructive 红)。 */
@Composable
private fun RestartConfirmDialog(processedCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quicksort_restart_confirm_title)) },
        text = { Text(stringResource(R.string.quicksort_restart_confirm_text, processedCount)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.quicksort_restart_confirm_ok),
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

/** 兜底 Sheet(下划/右划/长按入口):当前照片的保留 / 跳过;删除与移动随后续任务接入。 */
@Composable
private fun FallbackSheet(photo: MediaPhoto?, onKeep: () -> Unit, onSkip: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = photo?.displayName.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            TextButton(
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.quicksort_fallback_keep))
            }
            TextButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.quicksort_fallback_skip))
            }
        }
    }
}

/** 有未完成 session 时的选择 Sheet:继续上次 / 从头开始(计划 § 3.5.3)。 */
@Composable
private fun ResumeSheet(viewModel: QuickSortViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.quicksort_resume_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.quicksort_restart_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
            TextButton(
                onClick = viewModel::continueSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.quicksort_resume))
            }
            TextButton(
                onClick = viewModel::restartFromBeginning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.quicksort_restart),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * 新建 session 的配置对话框:目标相册多选(排除被整理相册自身,ViewModel 已过滤),
 * 允许不选(仅保留 / 删除可用);确认即创建并进入整理界面。
 */
@Composable
private fun SetupDialog(candidates: List<AlbumEntity>, onStart: (List<Long>) -> Unit, onDismiss: () -> Unit) {
    // SnapshotStateList 不可 Bundle 保存,不走 rememberSaveable(与打标签对话框同约定);
    // 配置是短操作,转屏即关可接受。
    val checked = remember { mutableStateListOf<Long>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quicksort_setup_title)) },
        text = {
            Column {
                if (candidates.isEmpty()) {
                    Text(
                        text = stringResource(R.string.quicksort_setup_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(candidates.size) { index ->
                            val album = candidates[index]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .toggleable(
                                        value = album.id in checked,
                                        onValueChange = { isChecked ->
                                            if (isChecked) checked.add(album.id) else checked.remove(album.id)
                                        }
                                    )
                            ) {
                                Checkbox(checked = album.id in checked, onCheckedChange = null)
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(checked.toList()) }) {
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
