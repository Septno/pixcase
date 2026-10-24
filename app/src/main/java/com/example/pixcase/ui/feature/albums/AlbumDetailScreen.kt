package com.example.pixcase.ui.feature.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.imageContentUri

private const val DETAIL_COLUMN_COUNT = 3

/**
 * 自定义相册详情页:相册内照片网格,点照片进全屏查看器。
 *
 * 查看器的翻页上下文仍是时间线的分页窗口(查看器复用时间线 ViewModel,
 * 见 NavGraph 注释)—— 这是当前已知限制,相册内连续翻页属后续增强。
 * 移出照片 / 详情页内多选属后续增强。
 */
@Composable
fun AlbumDetailScreen(
    onBack: () -> Unit,
    onPhotoClick: (Long) -> Unit,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    val albumName by viewModel.albumName.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        AlbumDetailTopBar(name = albumName, count = photos.size, onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(DETAIL_COLUMN_COUNT),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(photos.size, key = { photos[it].id }) { index ->
                val photo = photos[index]
                AsyncImage(
                    model = imageContentUri(photo.id),
                    contentDescription = photo.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onPhotoClick(photo.id) }
                )
            }
        }
    }
}

@Composable
private fun AlbumDetailTopBar(name: String, count: Int, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.viewer_back)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = quantityCountLabel(count),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 相册内照片计数:走 plurals 资源。 */
@Composable
private fun quantityCountLabel(count: Int): String {
    val resources = LocalContext.current.resources
    return resources.getQuantityString(R.plurals.albums_count, count, count)
}
