package com.example.pixcase.ui.feature.viewer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.mediastore.MediaStoreDataSource
import com.example.pixcase.data.model.MediaPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 查看器的补充信息。
 *
 * 列表投影只取 6 列(网格够用),尺寸信息要按 mediaId 单独补查 —— 只查当前正在看的那张,
 * 查不到就不显示,不阻塞看图。
 */
@HiltViewModel
class ViewerViewModel @Inject constructor(
    private val dataSource: MediaStoreDataSource
) : ViewModel() {

    private val _details = MutableStateFlow<MediaPhoto?>(null)

    /** 当前照片的详细字段(含 WIDTH / HEIGHT / SIZE);未加载到或查不到时为 null。 */
    val details: StateFlow<MediaPhoto?> = _details.asStateFlow()

    private var loadedId: Long? = null

    /** 切到某张照片时调用;同一张重复调用不会重复查库。 */
    fun loadDetails(mediaId: Long) {
        if (loadedId == mediaId) return
        loadedId = mediaId
        _details.value = null
        viewModelScope.launch { _details.value = dataSource.photoDetails(mediaId) }
    }
}
