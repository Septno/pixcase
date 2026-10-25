package com.example.pixcase.ui.feature.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.pixcase.R
import com.example.pixcase.data.db.entity.TagEntity

/**
 * 筛选面板(阶段 4):档位 Chip 组 + 三个包含式开关 + 标签多选(AND/OR)+
 * 一键清空;改动即时生效,「完成」收起。筛选与搜索词组合由 ViewModel 的 combine 完成。
 */
@Composable
internal fun FilterSheet(
    filters: SearchFilters,
    tags: List<TagEntity>,
    onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.search_filter_panel_title),
                style = MaterialTheme.typography.titleMedium
            )
            BucketFilterSection(filters = filters, onFiltersChange = onFiltersChange)
            FlagFilterSection(filters = filters, onFiltersChange = onFiltersChange)
            TagFilterSection(filters = filters, tags = tags, onFiltersChange = onFiltersChange)
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                TextButton(onClick = onClear) {
                    Text(stringResource(R.string.search_filter_clear))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.search_filter_done))
                }
            }
        }
    }
}

/** 档位筛选:时间范围 / 文件大小 / 分辨率 / MIME。 */
@Composable
internal fun BucketFilterSection(filters: SearchFilters, onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit) {
    FilterSectionLabel(stringResource(R.string.search_time_range))
    SingleChoiceRow(
        options = SearchTimeRange.entries.map { labelOf(it) },
        selectedIndex = SearchTimeRange.entries.indexOf(filters.timeRange),
        onSelect = { index -> onFiltersChange { it.copy(timeRange = SearchTimeRange.entries[index]) } }
    )
    FilterSectionLabel(stringResource(R.string.search_size_range))
    SingleChoiceRow(
        options = SearchSizeBucket.entries.map { labelOf(it) },
        selectedIndex = SearchSizeBucket.entries.indexOf(filters.sizeBucket),
        onSelect = { index -> onFiltersChange { it.copy(sizeBucket = SearchSizeBucket.entries[index]) } }
    )
    FilterSectionLabel(stringResource(R.string.search_dimension_range))
    SingleChoiceRow(
        options = SearchDimensionBucket.entries.map { labelOf(it) },
        selectedIndex = SearchDimensionBucket.entries.indexOf(filters.dimensionBucket),
        onSelect = { index ->
            onFiltersChange { it.copy(dimensionBucket = SearchDimensionBucket.entries[index]) }
        }
    )
    FilterSectionLabel(stringResource(R.string.search_mime))
    SingleChoiceRow(
        options = SearchMimeFilter.entries.map { labelOf(it) },
        selectedIndex = SearchMimeFilter.entries.indexOf(filters.mime),
        onSelect = { index -> onFiltersChange { it.copy(mime = SearchMimeFilter.entries[index]) } }
    )
}

/** 开关筛选:仅收藏 / 仅隐藏 / 仅有标签。 */
@Composable
internal fun FlagFilterSection(filters: SearchFilters, onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit) {
    SwitchRow(
        label = stringResource(R.string.search_filter_favorites),
        checked = filters.favoritesOnly,
        onCheckedChange = { value -> onFiltersChange { it.copy(favoritesOnly = value) } }
    )
    SwitchRow(
        label = stringResource(R.string.search_filter_hidden),
        checked = filters.hiddenOnly,
        onCheckedChange = { value -> onFiltersChange { it.copy(hiddenOnly = value) } }
    )
    SwitchRow(
        label = stringResource(R.string.search_filter_has_tag),
        checked = filters.hasTag,
        onCheckedChange = { value -> onFiltersChange { it.copy(hasTag = value) } }
    )
}

/** 标签多选(AND/OR 切换);无标签时整节隐藏。 */
@Composable
internal fun TagFilterSection(
    filters: SearchFilters,
    tags: List<TagEntity>,
    onFiltersChange: ((SearchFilters) -> SearchFilters) -> Unit
) {
    if (tags.isEmpty()) return
    FilterSectionLabel(stringResource(R.string.search_filter_tags))
    FilterChip(
        selected = filters.tagMatchAll,
        onClick = { onFiltersChange { it.copy(tagMatchAll = !it.tagMatchAll) } },
        label = {
            Text(
                stringResource(
                    if (filters.tagMatchAll) R.string.search_filter_match_all else R.string.search_filter_match_any
                )
            )
        },
        modifier = Modifier.padding(top = 4.dp)
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp)
    ) {
        tags.forEach { tag ->
            FilterChip(
                selected = tag.id in filters.tagIds,
                onClick = {
                    onFiltersChange { current ->
                        current.copy(
                            tagIds = if (tag.id in current.tagIds) {
                                current.tagIds - tag.id
                            } else {
                                current.tagIds + tag.id
                            }
                        )
                    }
                },
                label = { Text(tag.name) }
            )
        }
    }
}

@Composable
internal fun FilterSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
internal fun SingleChoiceRow(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        options.forEachIndexed { index, option ->
            FilterChip(
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                label = { Text(option) }
            )
        }
    }
}

@Composable
internal fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun labelOf(range: SearchTimeRange): String = when (range) {
    SearchTimeRange.ALL -> stringResource(R.string.search_time_all)
    SearchTimeRange.WEEK -> stringResource(R.string.search_time_week)
    SearchTimeRange.MONTH -> stringResource(R.string.search_time_month)
}

@Composable
internal fun labelOf(bucket: SearchSizeBucket): String = when (bucket) {
    SearchSizeBucket.ALL -> stringResource(R.string.search_size_all)
    SearchSizeBucket.SMALL -> stringResource(R.string.search_size_small)
    SearchSizeBucket.MEDIUM -> stringResource(R.string.search_size_medium)
    SearchSizeBucket.LARGE -> stringResource(R.string.search_size_large)
}

@Composable
internal fun labelOf(bucket: SearchDimensionBucket): String = when (bucket) {
    SearchDimensionBucket.ALL -> stringResource(R.string.search_dimension_all)
    SearchDimensionBucket.SMALL -> stringResource(R.string.search_dimension_small)
    SearchDimensionBucket.MEDIUM -> stringResource(R.string.search_dimension_medium)
    SearchDimensionBucket.LARGE -> stringResource(R.string.search_dimension_large)
}

@Composable
internal fun labelOf(mime: SearchMimeFilter): String = when (mime) {
    SearchMimeFilter.ALL -> stringResource(R.string.search_mime_all)
    SearchMimeFilter.JPEG -> stringResource(R.string.search_mime_jpeg)
    SearchMimeFilter.PNG -> stringResource(R.string.search_mime_png)
    SearchMimeFilter.OTHER -> stringResource(R.string.search_mime_other)
}
