package com.example.pixcase.ui.feature.search

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pixcase.data.db.dao.TagDao
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import com.example.pixcase.data.db.entity.TagEntity
import com.example.pixcase.data.repository.FilterContext
import com.example.pixcase.data.repository.PhotoRepository
import com.example.pixcase.data.repository.SearchRepository
import com.example.pixcase.data.repository.SearchSuggestion
import com.example.pixcase.data.repository.SearchedPhoto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 搜索层的一次性事件:分享走系统 chooser,启动动作必须在 Activity 侧。 */
sealed interface SearchEvent {
    data class SharePhotos(val uris: List<Uri>) : SearchEvent
}

/**
 * 阶段 4 搜索的 ViewModel:输入即搜(200ms debounce),空串即清结果;
 * 结果内多选(点击即选中,搜索态无查看器跳转 —— 结果不在时间线分页窗口内);
 * 分享复用时间线语义(ACTION_SEND_MULTIPLE)。
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val photoRepository: PhotoRepository,
    tagDao: TagDao
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters

    /** 一次搜索的快照:聚合结果 + 该结果集的筛选上下文(随词换血)。 */
    private data class SearchSnapshot(
        val results: List<SearchedPhoto> = emptyList(),
        val context: FilterContext = FilterContext(emptySet(), emptySet(), emptyMap())
    )

    private val snapshot: StateFlow<SearchSnapshot> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .mapLatest { query ->
            if (query.isBlank()) {
                SearchSnapshot()
            } else {
                searchRepository.recordQuery(query)
                val results = searchRepository.search(query)
                SearchSnapshot(results, searchRepository.filterContextFor(results.map { it.photo.id }))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), SearchSnapshot())

    /** 筛选与搜索词组合:改筛选只重过滤,不重查询。 */
    val results: StateFlow<List<SearchedPhoto>> = combine(snapshot, _filters) { snap, filters ->
        filterResults(snap.results, filters, nowSec(), snap.context)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 筛选面板的标签候选。 */
    val tags: StateFlow<List<TagEntity>> = tagDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 搜索建议:命中的标签 / 相册名(标签按用量降序在前),随输入即时刷新。 */
    val suggestions: StateFlow<List<SearchSuggestion>> = _query
        .mapLatest { searchRepository.suggestions(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    /** 搜索历史(最近 20 条,去重置顶)。 */
    val history: StateFlow<List<SearchHistoryEntity>> = searchRepository.observeHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val _selectedIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedIds: StateFlow<Set<Long>> = _selectedIds

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private fun nowSec(): Long = System.currentTimeMillis() / MILLIS_PER_SECOND

    fun onQueryChange(value: String) {
        _query.value = value
        // 新关键词意味着结果集换血,选中集一并清空
        _selectedIds.value = emptySet()
    }

    fun updateFilters(transform: (SearchFilters) -> SearchFilters) {
        _filters.value = transform(_filters.value)
    }

    fun clearFilters() {
        _filters.value = SearchFilters()
    }

    fun deleteHistory(query: String) {
        viewModelScope.launch { searchRepository.deleteHistory(query) }
    }

    fun toggleSelection(photoId: Long) {
        _selectedIds.value = if (photoId in _selectedIds.value) {
            _selectedIds.value - photoId
        } else {
            _selectedIds.value + photoId
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    fun shareSelection() {
        val ids = _selectedIds.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val uris = photoRepository.photosByIds(ids).map { it.uri }
            _events.send(SearchEvent.SharePhotos(uris))
        }
    }

    companion object {
        /** 输入即搜的防抖(计划 § 六 阶段 4:200ms)。 */
        const val SEARCH_DEBOUNCE_MILLIS = 200L
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private const val MILLIS_PER_SECOND = 1_000L
    }
}
