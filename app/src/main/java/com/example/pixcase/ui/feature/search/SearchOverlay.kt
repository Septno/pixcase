package com.example.pixcase.ui.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.SearchHistoryEntity
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.data.repository.SearchHitType
import com.example.pixcase.data.repository.SearchSuggestion
import com.example.pixcase.data.repository.SearchedPhoto
import com.example.pixcase.ui.feature.timeline.launchPhotoShare

/**
 * 关键字高亮的命中区间(大小写不敏感、不重叠),纯函数便于单测;
 * 下标在原字符串坐标系,高亮渲染见 [highlighted]。
 */
internal fun highlightRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var index = 0
    while (index <= text.length - needle.length) {
        if (text.regionMatches(index, needle, 0, needle.length, ignoreCase = true)) {
            ranges.add(index until index + needle.length)
            index += needle.length
        } else {
            index++
        }
    }
    return ranges
}

/** 命中片段加粗高亮的展示文本。 */
@Composable
internal fun highlighted(text: String, query: String): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    append(text)
    val highlight = SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    highlightRanges(text, query).forEach { range ->
        addStyle(highlight, range.start, range.endInclusive + 1)
    }
}

/** 时间线顶部的折叠搜索条:点击展开全屏搜索。 */
@Composable
fun CollapsedSearchBar(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.search_hint),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

/**
 * 全屏搜索覆盖层:输入即搜(200ms debounce)、类型 Chip 过滤、结果网格(点击即选中 ——
 * 结果不在时间线分页窗口内,不提供查看器跳转)、多选分享、空态 + 打标签新手引导。
 */
@Composable
fun SearchOverlay(onDismiss: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var hitTypeFilter by remember { mutableStateOf<SearchHitType?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(onBack = onDismiss)
    SearchEventsEffect(viewModel, snackbarHostState)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            SearchInputBar(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onClose = onDismiss,
                onOpenFilters = { showFilterSheet = true },
                activeFilterCount = filters.activeCount
            )
            FilterChipRow(filter = hitTypeFilter, onFilterChange = { hitTypeFilter = it })
            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                SuggestionRow(suggestions = suggestions, onPick = viewModel::onQueryChange)
            }
            val byHitType = results.filter { hitTypeFilter == null || hitTypeFilter in it.hitTypes }
            SearchBody(
                query = query,
                results = byHitType,
                history = history,
                selectedIds = selectedIds,
                onQueryChange = viewModel::onQueryChange,
                onDeleteHistory = viewModel::deleteHistory,
                onToggle = viewModel::toggleSelection,
                modifier = Modifier.weight(1f)
            )
            if (selectedIds.isNotEmpty()) {
                ShareBar(onShare = viewModel::shareSelection)
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
    if (showFilterSheet) {
        FilterSheet(
            filters = filters,
            tags = tags,
            onFiltersChange = { transform -> viewModel.updateFilters(transform) },
            onClear = viewModel::clearFilters,
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun SearchInputBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    onOpenFilters: () -> Unit,
    activeFilterCount: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.viewer_back)
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_cancel)
                        )
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        )
        BadgedBox(
            badge = {
                if (activeFilterCount > 0) {
                    Badge { Text("$activeFilterCount") }
                }
            }
        ) {
            IconButton(onClick = onOpenFilters) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = stringResource(R.string.search_filter_open)
                )
            }
        }
    }
}

@Composable
private fun FilterChipRow(filter: SearchHitType?, onFilterChange: (SearchHitType?) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        FilterChip(
            selected = filter == null,
            onClick = { onFilterChange(null) },
            label = { Text(stringResource(R.string.search_filter_all)) }
        )
        FilterChip(
            selected = filter == SearchHitType.FILENAME,
            onClick = { onFilterChange(SearchHitType.FILENAME) },
            label = { Text(stringResource(R.string.search_filter_filename)) }
        )
        FilterChip(
            selected = filter == SearchHitType.ALBUM,
            onClick = { onFilterChange(SearchHitType.ALBUM) },
            label = { Text(stringResource(R.string.search_filter_album)) }
        )
        FilterChip(
            selected = filter == SearchHitType.TAG,
            onClick = { onFilterChange(SearchHitType.TAG) },
            label = { Text(stringResource(R.string.search_filter_tag)) }
        )
    }
}

@Composable
private fun SearchResultGrid(
    results: List<SearchedPhoto>,
    query: String,
    selectedIds: Set<Long>,
    onToggle: (Long) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(SEARCH_GRID_COLUMNS),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(results.size, key = { results[it].photo.id }) { index ->
            val result = results[index]
            SearchPhotoCell(
                photo = result.photo,
                query = query,
                isSelected = result.photo.id in selectedIds,
                onToggle = { onToggle(result.photo.id) }
            )
        }
    }
}

/** 结果格:缩略图 + 命中高亮的文件名 + 选中角标(选中视觉与时间线网格一致)。 */
@Composable
private fun SearchPhotoCell(photo: MediaPhoto, query: String, isSelected: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onToggle)
            .padding(2.dp)
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxSize()
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = SELECTED_SCRIM_ALPHA))
                        .border(2.dp, MaterialTheme.colorScheme.primary)
                )
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }
        Text(
            text = highlighted(photo.displayName, query),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ShareBar(onShare: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Outlined.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun IdleHint() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.search_idle_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 一次性事件:分享走系统 chooser。 */
@Composable
private fun SearchEventsEffect(viewModel: SearchViewModel, snackbarHostState: SnackbarHostState) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.SharePhotos ->
                    launchPhotoShare(context, event.uris) { messageRes ->
                        snackbarHostState.showSnackbar(context.getString(messageRes))
                    }
            }
        }
    }
}

/** 覆盖层正文三态:待输入(历史)/ 空结果 / 结果网格。 */
@Composable
private fun SearchBody(
    query: String,
    results: List<SearchedPhoto>,
    history: List<SearchHistoryEntity>,
    selectedIds: Set<Long>,
    onQueryChange: (String) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onToggle: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        when {
            query.isBlank() -> HistoryArea(history = history, onPick = onQueryChange, onDelete = onDeleteHistory)
            results.isEmpty() -> SearchEmptyState()
            else -> SearchResultGrid(results = results, query = query, selectedIds = selectedIds, onToggle = onToggle)
        }
    }
}

/** 输入态下的建议行:命中的标签 / 相册名,点选即以此为词。 */
@Composable
private fun SuggestionRow(suggestions: List<SearchSuggestion>, onPick: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onPick(suggestion.text) },
                label = {
                    Text(
                        text = stringResource(
                            if (suggestion.isTag) R.string.search_suggestion_tag else R.string.search_suggestion_album,
                            suggestion.text
                        )
                    )
                }
            )
        }
    }
}

/** 待输入态正文:有历史显示最近搜索(点选回填、单条删除),无历史给输入提示。 */
@Composable
private fun HistoryArea(history: List<SearchHistoryEntity>, onPick: (String) -> Unit, onDelete: (String) -> Unit) {
    if (history.isEmpty()) {
        IdleHint()
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.search_history_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(history.size, key = { history[it].id }) { index ->
                val entry = history[index]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(entry.query) }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = entry.query,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onDelete(entry.query) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_delete)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchEmptyState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.search_empty_title),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = stringResource(R.string.search_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

/** 无可接收分享应用时的提示文案资源由时间线公共实现处理(两处共用同一入口)。 */

private const val SEARCH_GRID_COLUMNS = 3
private const val SELECTED_SCRIM_ALPHA = 0.3f
