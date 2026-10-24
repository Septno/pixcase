package com.example.pixcase.ui.feature.albums

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.mediastore.MediaSummary
import com.example.pixcase.data.repository.AlbumsRepository
import com.example.pixcase.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
 * 相册 Tab 的 ViewModel:自定义相册(封面 + 条数)+ 智能相册概览 + 新建 / 重命名 / 删除。
 *
 * 智能相册概览数据面在 [PhotoRepository]:截图直查 MediaStore(RELATIVE_PATH,仅 Q+ 有意义),
 * 收藏按 API 分流(30+ MediaStore IS_FAVORITE,更低版本 Room 覆盖层)。
 */
@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumsRepository: AlbumsRepository,
    photoRepository: PhotoRepository
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

    /** 收藏智能相册概览;收藏在任何 API 都有数据源,首帧先给空概览。 */
    val favoritesSummary: StateFlow<MediaSummary> = photoRepository.observeFavoritesSummary()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MediaSummary(0, null))

    /** 截图智能相册概览;RELATIVE_PATH 列 Q 起才有,低版本保持 null(卡片不展示)。 */
    val screenshotsSummary: StateFlow<MediaSummary?> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            photoRepository.observeScreenshotsSummary()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
        } else {
            MutableStateFlow(null)
        }

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
