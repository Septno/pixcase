package com.example.pixcase.ui.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * 相册详情页:相册内照片按「最近加入相册优先」展示。
 *
 * 数据面 = Room 的 mediaId 顺序流 + MediaStore 按需补查(照片可能已被系统删除,
 * 补查缺席即跳过)。照片被真删后的孤儿关联由删除流程统一清理(陷阱 3)。
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    albumsRepository: AlbumsRepository,
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

    companion object {
        const val ALBUM_ID_KEY = "albumId"
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
