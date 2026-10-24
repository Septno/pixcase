package com.example.pixcase.ui.feature.albums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems

/**
 * 智能相册详情页(收藏 / 截图):复用自定义相册详情页的骨架(顶栏 + 照片网格),
 * 数据面为 MediaStore 条件分页(低版本收藏走 Room 覆盖层,见 PhotoRepository)。
 * 查看器的翻页上下文仍是时间线的分页窗口,与自定义相册详情页一致。
 */
@Composable
fun SmartAlbumDetailScreen(
    onBack: () -> Unit,
    onPhotoClick: (Long) -> Unit,
    viewModel: SmartAlbumDetailViewModel = hiltViewModel()
) {
    val title = stringResource(viewModel.titleResId)
    val photos = viewModel.photos.collectAsLazyPagingItems()

    Column(modifier = Modifier.fillMaxSize()) {
        AlbumDetailTopBar(name = title, count = photos.itemCount, onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(DETAIL_COLUMN_COUNT),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(
                count = photos.itemCount,
                // peek 判空定 key:刷新换代瞬间旧下标可能已越界,退化为位置 key 不崩溃。
                key = { index -> photos.peek(index)?.id ?: index }
            ) { index ->
                val photo = photos[index]
                // enablePlaceholders = false 时已加载下标不会是占位;这里的 null 分支
                // 只为满足类型穷尽,不渲染。
                if (photo != null) {
                    DetailPhotoCell(photo = photo, onPhotoClick = onPhotoClick)
                }
            }
        }
    }
}
