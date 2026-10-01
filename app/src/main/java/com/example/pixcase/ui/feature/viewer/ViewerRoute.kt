package com.example.pixcase.ui.feature.viewer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import com.example.pixcase.R
import com.example.pixcase.data.mediastore.OriginalImage
import com.example.pixcase.data.model.MediaPhoto
import com.example.pixcase.ui.feature.timeline.TimelineViewModel
import com.example.pixcase.ui.feature.timeline.photosIn

private const val CAPTION_ALPHA = 0.45f

/**
 * 全屏查看器。
 *
 * 数据**直接复用时间线的分页流**:[timelineViewModel] 由 NavGraph 传入时间线那一份实例,
 * 它的 `timelineFlow` 已经 `cachedIn` —— cachedIn 会把已加载的页重放给新的订阅者,
 * 所以这里不需要任何额外查询就能拿到与网格完全一致的窗口。
 *
 * 前提是 `cachedIn` 之后没有再接 `map` / `insertSeparators`(那样会让重放失效),
 * 时间线那边保持这个约束。
 */
@Composable
fun ViewerRoute(
    photoId: Long,
    timelineViewModel: TimelineViewModel,
    onBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    val items = timelineViewModel.timelineFlow.collectAsLazyPagingItems()
    // 用 State 而不是直接取 value:pageCount 的 lambda 要读到最新值。
    val photosState = remember { derivedStateOf { photosIn(items.itemSnapshotList.items) } }
    val photos = photosState.value
    val pagerState = rememberPagerState(pageCount = { photosState.value.size })

    LocateInitialPhoto(
        photos = photos,
        photoId = photoId,
        pagerState = pagerState,
        onPhotoMissing = onBack
    )

    val currentPhoto = photos.getOrNull(pagerState.settledPage)
    LaunchedEffect(currentPhoto?.id) {
        currentPhoto?.let { viewModel.loadDetails(it.id) }
    }
    val details by viewModel.details.collectAsState()

    val zoom = rememberViewerZoomState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // 放大后禁掉翻页,避免与页内拖动抢手势。
            userScrollEnabled = !zoom.isZoomed
        ) { page ->
            photos.getOrNull(page)?.let { photo -> ViewerPage(photo, zoom) }
        }

        currentPhoto?.let { photo ->
            PhotoCaption(
                photo = photo,
                details = details,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }

        ViewerBackButton(
            onBack = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

/**
 * 首次把 pager 定位到被点击的那张。
 *
 * 数据到位后仍找不到(进程被回收后重建、或照片已被删除)就回调 [onPhotoMissing] 退回网格;
 * 数据到位前不判断 —— 首帧窗口本来就是空的。定位只做一次,之后用户划到哪就是哪。
 */
@Composable
private fun LocateInitialPhoto(
    photos: List<MediaPhoto>,
    photoId: Long,
    pagerState: PagerState,
    onPhotoMissing: () -> Unit
) {
    var located by remember { mutableStateOf(false) }
    // 缩放时本界面每帧都会重组,不 remember 的话每帧都要扫一遍已加载窗口。
    val index = remember(photos, photoId) { photos.indexOfFirst { it.id == photoId } }

    LaunchedEffect(photos, located) {
        if (located) return@LaunchedEffect
        when {
            index >= 0 -> {
                pagerState.scrollToPage(index)
                located = true
            }
            photos.isNotEmpty() -> onPhotoMissing()
        }
    }
}

@Composable
private fun ViewerBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onBack,
        // 查看器铺满屏幕,系统栏不会替我们让位,按钮要自己避开状态栏。
        modifier = modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.viewer_back),
            tint = Color.White
        )
    }
}

@Composable
private fun ViewerPage(photo: MediaPhoto, zoom: ViewerZoomState) {
    val context = LocalContext.current
    // 传 OriginalImage 而不是 photo.uri:同一个 content Uri 会被照片墙那套缩略图
    // Fetcher 抢先命中,换 model 类型是拿到原图的唯一可靠办法。
    // remember:缩放时本页每个手势帧都会重组,不 remember 会每帧重建请求对象。
    val request = remember(photo.uri) {
        ImageRequest.Builder(context)
            .data(OriginalImage(photo.uri))
            .build()
    }
    val painter = rememberAsyncImagePainter(request)
    val painterState by painter.state.collectAsState()

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        zoom.transform(zoomChange, panChange)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { zoom.containerSize = it }
            // 未放大时不吃单指拖动(canPan 为 false),手势落到外层 pager 上翻页;
            // 放大后子级先消费,pager 就收不到了。
            .transformable(
                state = transformableState,
                lockRotationOnZoomPan = true,
                canPan = { zoom.isZoomed }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (painterState is AsyncImagePainter.State.Error) {
            Text(
                text = stringResource(R.string.viewer_load_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Image(
                painter = painter,
                contentDescription = photo.displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    }
            )
            if (painterState is AsyncImagePainter.State.Loading) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

/** 底部信息条:文件名 + 尺寸(尺寸查不到就只显示文件名)。 */
@Composable
private fun PhotoCaption(photo: MediaPhoto, details: MediaPhoto?, modifier: Modifier = Modifier) {
    val dimensionSuffix = details
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { " · ${it.width}×${it.height}" }
        .orEmpty()

    Text(
        text = "${photo.displayName}$dimensionSuffix",
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = CAPTION_ALPHA))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}
