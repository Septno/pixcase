package com.example.pixcase.ui.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * 相册详情页:相册内照片按「最近加入相册优先」展示,支持多选移出与手动设为封面。
 *
 * 数据面 = Room 的 mediaId 顺序流 + MediaStore 按需补查(照片可能已被系统删除,
 * 补查缺席即跳过)。照片被真删后的孤儿关联由删除流程统一清理(陷阱 3)。
 * 多选语义与时间线一致:长按进入,选中集非空即多选态,选空自动退出;BACK 退出多选。
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumsRepository: AlbumsRepository,
    photoRepository: PhotoRepository
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle[ALBUM_ID_KEY])

    val albumName: StateFlow<String> = albumsRepository.observeUserAlbums()
        .map { albums -> albums.firstOrNull { it.id == albumId }?.name.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), "")

    val photos: StateFlow<List<MediaPhoto>> = albumsRepository.observeMediaIds(albumId)
        .flatMapLatest { mediaIds ->
            flow { emit(photoRepository.photosByIds(mediaIds)) }.map { photos ->
                val byId = photos.associateBy { it.id }
                mediaIds.mapNotNull { byId[it] }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 多选集合:非空即多选态(同时间线约定)。 */
    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _events = Channel<AlbumDetailEvent>(Channel.BUFFERED)
    val events: Flow<AlbumDetailEvent> = _events.receiveAsFlow()

    /** 长按进入多选 / 追加选中。 */
    fun selectPhoto(photoId: Long) {
        _selectedIds.value = _selectedIds.value + photoId
    }

    /** 多选态下点击:切换该张的选中;未选中集为空时由 UI 直接打开查看器。 */
    fun togglePhotoSelection(photoId: Long) {
        _selectedIds.value = if (photoId in _selectedIds.value) {
            _selectedIds.value - photoId
        } else {
            _selectedIds.value + photoId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /**
     * 把选中照片移出相册:仅解除 album_photo 关联,原图不动(封面顺延在仓库层)。
     * 移出后网格经 Room 流即时刷新,选中集一并清空。
     */
    fun removeSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            ids.forEach { albumsRepository.removePhoto(albumId, it, now) }
            clearSelection()
            _events.send(AlbumDetailEvent.Message(R.string.msg_removed_from_album, ids.size))
        }
    }

    /** 选中 1 张时可用:把它设为相册封面(手动指定,覆盖「最近一张」自动封面)。 */
    fun setCoverForSelection() {
        val mediaId = _selectedIds.value.singleOrNull() ?: return
        viewModelScope.launch {
            albumsRepository.setCover(albumId, mediaId, System.currentTimeMillis())
            clearSelection()
            _events.send(AlbumDetailEvent.Message(R.string.msg_cover_set))
        }
    }

    companion object {
        const val ALBUM_ID_KEY = "albumId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
