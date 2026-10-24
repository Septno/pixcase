package com.example.pixcase.ui.feature.timeline

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.TagEntity
import com.example.pixcase.data.mediastore.isValidDisplayNameBase

/**
 * 多选模式的顶栏:退出按钮 + 已选计数。
 *
 * 不用 TopAppBar:多选是时间线内的临时状态,自带 statusBars 内嵌的 Surface 即可,
 * 避免与 NavHost 外层 Scaffold 的系统栏内嵌叠加。
 */
@Composable
internal fun SelectionTopBar(count: Int, onClose: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.action_cancel)
                )
            }
            Text(
                text = quantityCountLabel(count),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/** 已选计数:走 plurals 资源,英文里 one / other 各自成句。 */
@Composable
internal fun quantityCountLabel(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.selection_count, count, count)
}

/**
 * 多选模式的底部操作栏。
 *
 * 操作共 6 个,窄屏一行放不下,Row 可横向滚动(系统相册同款交互);顺序按危险程度递增,
 * 删除排最末。收藏按钮按 [allFavorited] 切换形态:true = 所选全部已收藏,按钮变「取消收藏」;
 * false / null = 任一未收藏,按钮为「收藏」。
 */
@Composable
internal fun SelectionActionBar(
    allFavorited: Boolean?,
    onToggleFavorite: () -> Unit,
    onAddTag: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onSetWallpaper: () -> Unit,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val favorited = allFavorited == true
            SelectionActionButton(
                icon = if (favorited) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                label = stringResource(if (favorited) R.string.action_unfavorite else R.string.action_favorite),
                onClick = onToggleFavorite
            )
            SelectionActionButton(
                icon = Icons.AutoMirrored.Outlined.Label,
                label = stringResource(R.string.action_add_tag),
                onClick = onAddTag
            )
            SelectionActionButton(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.action_share),
                onClick = onShare
            )
            SelectionActionButton(
                icon = Icons.Outlined.DriveFileRenameOutline,
                label = stringResource(R.string.action_rename),
                onClick = onRename
            )
            SelectionActionButton(
                icon = Icons.Outlined.Wallpaper,
                label = stringResource(R.string.action_set_wallpaper),
                onClick = onSetWallpaper
            )
            SelectionActionButton(
                icon = Icons.Outlined.VisibilityOff,
                label = stringResource(R.string.action_hide),
                onClick = onHide
            )
            SelectionActionButton(
                icon = Icons.Outlined.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun SelectionActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * 重命名对话框:只编辑「不含扩展名的主体名」,扩展名由 ViewModel 拼回 ——
 * 从根上避免用户改坏 MIME 关联。校验错误在行内展示,不通过时不关闭对话框。
 */
@Composable
internal fun RenameDialog(currentBaseName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(currentBaseName) }
    var errorResId by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    errorResId = 0
                },
                label = { Text(stringResource(R.string.rename_input_label)) },
                isError = errorResId != 0,
                supportingText = if (errorResId != 0) {
                    { Text(stringResource(errorResId)) }
                } else {
                    null
                },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmed = text.trim()
                when {
                    trimmed.isEmpty() -> errorResId = R.string.rename_error_empty
                    !isValidDisplayNameBase(trimmed) -> errorResId = R.string.rename_error_invalid
                    else -> onConfirm(trimmed)
                }
            }) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * 打标签对话框:列出全部标签,勾选语义是「该标签同时打在所有选中照片上」,
 * 初始勾选由 [checkedTagIds] 传入(交集);底部可就地新建标签。
 * 最终勾选集通过 [onConfirm] 回传,差量计算在 ViewModel。
 */
@Composable
internal fun TagDialog(
    tags: List<TagEntity>,
    checkedTagIds: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onCreateTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val checked = rememberSaveable(checkedTagIds) {
        mutableStateListOf<Long>().also { it.addAll(checkedTagIds) }
    }
    var newTagName by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tag_dialog_title)) },
        text = {
            Column {
                LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                    items(tags.size) { index ->
                        TagCheckRow(
                            tag = tags[index],
                            checked = tags[index].id in checked,
                            onToggle = { isChecked ->
                                if (isChecked) checked.add(tags[index].id) else checked.remove(tags[index].id)
                            }
                        )
                    }
                }
                NewTagInputRow(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    onCreate = {
                        onCreateTag(newTagName)
                        newTagName = ""
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(checked.toSet()) }) {
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

@Composable
private fun TagCheckRow(tag: TagEntity, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onToggle)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun NewTagInputRow(value: String, onValueChange: (String) -> Unit, onCreate: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.tag_dialog_new_hint)) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onCreate) {
            Text(stringResource(R.string.tag_dialog_create))
        }
    }
}
