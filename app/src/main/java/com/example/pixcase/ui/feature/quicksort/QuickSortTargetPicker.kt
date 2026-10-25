package com.example.pixcase.ui.feature.quicksort

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.AlbumEntity
import com.example.pixcase.data.mediastore.imageContentUri
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.QuickSortRepository
import com.example.pixcase.data.repository.normalizeAlbumName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 选择器的结果事件:保存成功 / 名称非法 / 名称重复 / 保存空目标提示。 */
sealed interface TargetPickerEvent {
    data object Saved : TargetPickerEvent
    data object EmptySelectionHint : TargetPickerEvent
    data class InvalidName(val duplicate: Boolean) : TargetPickerEvent
}

/**
 * 目标相册选择器(计划 § 3.5.5「Dock 末尾 ➕」):全量自定义相册多选(排除被整理相册自身),
 * 勾选顺序 = Dock 显示顺序(position);就地新建相册并自动勾选;保存写 target 表,
 * 取消 / 返回丢弃变更。
 */
@HiltViewModel
class QuickSortTargetPickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumsRepository: AlbumsRepository,
    private val quickSortRepository: QuickSortRepository
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle[ALBUM_ID_KEY])

    /** 全量自定义相册(排除被整理相册自身)。 */
    val albums: StateFlow<List<AlbumEntity>> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.filterNot { it.id == albumId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 勾选的目标,顺序 = 勾选先后(保存时按此写 position)。 */
    private val _selection = MutableStateFlow<List<Long>>(emptyList())
    val selection: StateFlow<List<Long>> = _selection

    private val _events = MutableStateFlow<TargetPickerEvent?>(null)
    val events: StateFlow<TargetPickerEvent?> = _events

    private var emptyHintShown = false

    init {
        viewModelScope.launch {
            _selection.value = quickSortRepository.observeTargets(albumId).first()
                .map { it.targetAlbumId }
        }
    }

    /** 点条目切换勾选:勾上追加到末尾,取消原地移除。 */
    fun toggle(albumId: Long) {
        _selection.value = if (albumId in _selection.value) {
            _selection.value - albumId
        } else {
            _selection.value + albumId
        }
    }

    /**
     * 就地新建相册并自动勾选;名字非法(空白/超长)或与现有相册重名时发事件,不入库。
     */
    fun createAlbum(rawName: String) {
        val name = normalizeAlbumName(rawName) ?: run {
            _events.value = TargetPickerEvent.InvalidName(duplicate = false)
            return
        }
        viewModelScope.launch {
            val duplicate = albumsRepository.observeUserAlbums().first().any { it.name == name }
            if (duplicate) {
                _events.value = TargetPickerEvent.InvalidName(duplicate = true)
                return@launch
            }
            val newId = albumsRepository.createAlbum(name, System.currentTimeMillis()) ?: return@launch
            _selection.value = _selection.value + newId
        }
    }

    /**
     * 保存:按勾选顺序写 target 表;勾选为空时先提示「仅保留 / 删除可用」再放行
     * (第二次点保存才真正保存 —— 避免提示一闪而过)。保存成功经 Saved 事件返回。
     */
    fun save() {
        viewModelScope.launch {
            if (_selection.value.isEmpty() && !emptyHintShown) {
                emptyHintShown = true
                _events.value = TargetPickerEvent.EmptySelectionHint
                return@launch
            }
            quickSortRepository.updateTargets(albumId, _selection.value)
            _events.value = TargetPickerEvent.Saved
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    companion object {
        /** 必须与 NavGraph 的 QUICK_SORT_ARG 同值:SavedStateHandle 按导航参数名取值。 */
        const val ALBUM_ID_KEY = "quickSortAlbumId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * 目标相册选择器页面:顶栏(← + 标题 + 保存)+ 已选计数 + 相册多选网格(封面 + 名称 + 勾选)
 * + 底部就地新建行;Snackbar 反馈名称校验与空选择提示。
 */
@Composable
fun QuickSortTargetPickerRoute(onBack: () -> Unit, viewModel: QuickSortTargetPickerViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val event by viewModel.events.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var newAlbumName by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(event) {
        handlePickerEvent(event, context, snackbarHostState, onBack = onBack, onConsume = viewModel::consumeEvent)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TargetPickerTopBar(
                selectedCount = selection.size,
                totalCount = albums.size,
                onBack = onBack,
                onSave = viewModel::save
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(TARGET_PICKER_COLUMNS),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albums.size) { index ->
                    val album = albums[index]
                    TargetAlbumCell(
                        album = album,
                        checked = album.id in selection,
                        onToggle = { viewModel.toggle(album.id) }
                    )
                }
            }
            NewAlbumRow(
                value = newAlbumName,
                onValueChange = { newAlbumName = it },
                onCreate = {
                    viewModel.createAlbum(newAlbumName)
                    newAlbumName = ""
                }
            )
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 顶栏:←(丢弃变更)+ 标题 + 已选 N/M + 保存。 */
@Composable
private fun TargetPickerTopBar(selectedCount: Int, totalCount: Int, onBack: () -> Unit, onSave: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.viewer_back)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.quicksort_target_picker_title),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.quicksort_target_picker_selected, selectedCount, totalCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.quicksort_target_picker_save))
            }
        }
    }
}

/** 相册格:封面缩略图 + 名称 + 勾选角标,点格子即切换。 */
@Composable
private fun TargetAlbumCell(album: AlbumEntity, checked: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(4.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                if (album.coverMediaId != null) {
                    AsyncImage(
                        model = imageContentUri(album.coverMediaId),
                        contentDescription = album.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = album.name.take(1),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(32.dp))
        }
        Text(
            text = album.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

/** 底部固定行:新建相册名输入 + 创建(校验在 ViewModel,错误走 Snackbar)。 */
@Composable
private fun NewAlbumRow(value: String, onValueChange: (String) -> Unit, onCreate: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(stringResource(R.string.album_picker_new_hint)) },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onCreate, enabled = value.isNotBlank()) {
                Text(stringResource(R.string.tag_dialog_create))
            }
        }
    }
}

/** 选择器事件消费:保存即返回;空选择与名称校验走 Snackbar。 */
private suspend fun handlePickerEvent(
    event: TargetPickerEvent?,
    context: Context,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onConsume: () -> Unit
) {
    when (event) {
        TargetPickerEvent.Saved -> onBack()
        TargetPickerEvent.EmptySelectionHint -> {
            snackbarHostState.showSnackbar(context.getString(R.string.quicksort_targets_empty_warning))
            onConsume()
        }
        is TargetPickerEvent.InvalidName -> {
            val messageRes = if (event.duplicate) R.string.msg_album_duplicate else R.string.msg_album_invalid
            snackbarHostState.showSnackbar(context.getString(messageRes))
            onConsume()
        }
        null -> Unit
    }
}

private const val TARGET_PICKER_COLUMNS = 3
