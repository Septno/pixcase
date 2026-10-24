package com.example.pixcase.ui.feature.albums

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 智能相册详情页:按路由 kind 把收藏 / 截图分流到 PhotoRepository 的对应数据源。
 * 收藏在 30+ 走 MediaStore 条件分页(IS_FAVORITE)、更低版本走 Room 收藏覆盖层;
 * 截图走 RELATIVE_PATH 条件分页(入口在低版本不出现)。标题与相册 Tab 卡片同名。
 */
@HiltViewModel
class SmartAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    photoRepository: PhotoRepository
) : ViewModel() {

    private val kind: String = checkNotNull(savedStateHandle[SMART_KIND_KEY])

    /** 顶栏标题的字符串资源 id,由 UI 解析。 */
    val titleResId: Int = when (kind) {
        SMART_FAVORITES -> R.string.albums_smart_favorites
        else -> R.string.albums_smart_screenshots
    }

    /** 智能相册内容;冷流经 cachedIn 供 LazyPagingItems 消费(同时间线)。 */
    val photos: Flow<PagingData<MediaPhoto>> = when (kind) {
        SMART_FAVORITES -> photoRepository.smartFavorites()
        else -> photoRepository.smartScreenshots()
    }.cachedIn(viewModelScope)

    companion object {
        const val SMART_KIND_KEY = "kind"
    }
}
