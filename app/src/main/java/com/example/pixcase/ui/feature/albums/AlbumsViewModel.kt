package com.example.pixcase.ui.feature.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.repository.AlbumsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 相册列表卡片的自定义相册条目。 */
data class AlbumCard(
    val id: Long,
    val name: String,
    val coverMediaId: Long?,
    val photoCount: Int
)

/**
 * 相册 Tab 的 ViewModel:自定义相册(封面 + 条数)+ 新建 / 重命名 / 删除。
 *
 * 智能相册(截图 / 收藏)不经过 Room,数据面在 [AlbumsScreen] 按 API 级别静态声明。
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumsRepository: AlbumsRepository
) : ViewModel() {

    val userAlbums: StateFlow<List<AlbumCard>> = combine(
        albumsRepository.observeUserAlbums(),
        albumsRepository.observeCounts()
    ) { albums, counts ->
        val countById = counts.associate { it.albumId to it.photoCount }
        albums.map { album ->
            AlbumCard(album.id, album.name, album.coverMediaId, countById[album.id] ?: 0)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 创建相册;名字非法时以 Snackbar 提示。 */
    fun createAlbum(rawName: String, onInvalid: () -> Unit) {
        viewModelScope.launch {
            if (albumsRepository.createAlbum(rawName, System.currentTimeMillis()) == null) onInvalid()
        }
    }

    fun renameAlbum(albumId: Long, rawName: String, onInvalid: () -> Unit) {
        viewModelScope.launch {
            if (!albumsRepository.renameAlbum(albumId, rawName, System.currentTimeMillis())) onInvalid()
        }
    }

    fun deleteAlbum(albumId: Long) {
        viewModelScope.launch { albumsRepository.deleteAlbum(albumId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
