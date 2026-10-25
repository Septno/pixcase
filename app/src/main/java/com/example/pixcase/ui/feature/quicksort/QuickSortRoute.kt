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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.AlbumEntity

/**
 * 快速整理路由:进入分流(Loading → NeedsChoice / NeedsConfig)→ Active 整理界面。
 * 配置阶段(选择/继续)只是 Sheet / Dialog,Active 才是真正的整理界面;取消配置即返回详情页。
 */
@Composable
fun QuickSortRoute(onBack: () -> Unit, viewModel: QuickSortViewModel = hiltViewModel()) {
    val stage by viewModel.stage.collectAsStateWithLifecycle()
    val albumName by viewModel.albumName.collectAsStateWithLifecycle()
    val candidateAlbums by viewModel.candidateAlbums.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()

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
        QuickSortStage.Active -> QuickSortScreen(
            albumName = albumName,
            processedCount = session?.processedCount ?: 0,
            totalCount = session?.totalCount ?: 0,
            onBack = { currentIndex -> viewModel.exit(currentIndex, onBack) }
        )
    }
}

/**
 * 整理界面(阶段 3.5 任务 6 的骨架):顶栏 = ←(退出自动保存)+「相册名 · 快速整理」+ 进度。
 * 卡片堆叠与手势反馈在任务 7 落地,当前正文为队列概览。
 */
@Composable
private fun QuickSortScreen(albumName: String, processedCount: Int, totalCount: Int, onBack: (Int) -> Unit) {
    // 卡片界面(任务 7)接管 currentIndex;当前骨架持 0,退出保存走同一通道。
    val currentIndex = 0
    BackHandler { onBack(currentIndex) }
    val pendingCount = (totalCount - processedCount).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        QuickSortTopBar(
            albumName = albumName,
            processedCount = processedCount,
            totalCount = totalCount,
            onBack = { onBack(currentIndex) }
        )
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.quicksort_pending_summary, pendingCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 顶栏:返回(退出自动保存)+ 标题 + 进度 N/M;⟲ 重启按钮随任务 7 的二次确认一起上。 */
@Composable
private fun QuickSortTopBar(albumName: String, processedCount: Int, totalCount: Int, onBack: () -> Unit) {
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
