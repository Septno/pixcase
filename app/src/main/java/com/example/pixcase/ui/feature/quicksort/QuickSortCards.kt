package com.example.pixcase.ui.feature.quicksort

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.pixcase.R
import com.example.pixcase.data.model.MediaPhoto
import kotlin.math.abs
import kotlinx.coroutines.launch

/** 可见卡片层数:最上层 + 后续两张预览(计划 § 3.5.5)。 */
internal const val VISIBLE_CARDS = 3

/** 每深一层的缩放递减:1.0 → 0.95 → 0.90 等差(计划 § 3.5.5 动画规格)。 */
internal const val CARD_SCALE_STEP = 0.05f

/** 每深一层的纵向下移与透明度递减。 */
private val CARD_OFFSET_Y_STEP = 16.dp
private const val CARD_ALPHA_STEP = 0.15f

/** 拖拽判定阈值(超出即视为定向手势)。 */
internal val DRAG_THRESHOLD = 140.dp

/** 拖拽偏移 → 卡片旋转角(px / 20,计划 § 3.5.5)。 */
private const val ROTATION_DIVISOR = 20f

/** 飞出动画:spring stiffness=400, dampingRatio=0.6(计划 § 3.5.5)。 */
private const val FLY_OUT_STIFFNESS = 400f
private const val FLY_OUT_DAMPING_RATIO = 0.6f

/** 新卡上浮:0.95 → 1.0,250ms(计划 § 3.5.5)。 */
private const val RISE_DURATION_MILLIS = 250

private val CARD_CORNER_RADIUS = 16.dp
private const val CARD_WIDTH_FRACTION = 0.8f
private const val CARD_ASPECT_RATIO = 0.75f
private val HINT_PADDING = 24.dp

/** 松手判定:按拖拽终点偏移决定卡片去向(计划 § 3.5.2 手势语义)。 */
internal enum class DragOutcome {
    /** 左划超阈值:保留,卡片左飞。 */
    Keep,

    /** 右划 / 纵向超阈值:弹回并弹底部 Sheet 兜底(长按同入口)。 */
    Fallback,

    /** 未过阈值:弹回原位。 */
    SnapBack
}

/** 提示层透明度:|位移| / 阈值,夹在 0..1(计划 § 3.5.5,纯函数便于单测)。 */
internal fun hintAlpha(delta: Float, thresholdPx: Float): Float =
    if (thresholdPx <= 0f) 0f else (abs(delta) / thresholdPx).coerceIn(0f, 1f)

/** 松手去向判定(纯函数便于单测):左划 Keep,右划/纵向 Fallback,其余 SnapBack。 */
internal fun dragOutcome(dx: Float, dy: Float, thresholdPx: Float): DragOutcome = when {
    dx < -thresholdPx -> DragOutcome.Keep
    abs(dx) > thresholdPx || abs(dy) > thresholdPx -> DragOutcome.Fallback
    else -> DragOutcome.SnapBack
}

/**
 * 卡片堆叠:队首可拖,后两张静态预览;拖动实时联动 顶部「删除」/左侧「保留」提示层;
 * 松手按 [dragOutcome] 分流 —— Keep 走飞出动画(spring 400/0.6)后回调,其余弹回。
 * [photos] 为待处理队列(队首在前);key 用 mediaId,重排后同卡复用组合体与解码缓存。
 */
@Composable
internal fun PhotoCardStack(
    photos: List<MediaPhoto>,
    onKeep: () -> Unit,
    onFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thresholdPx = with(LocalDensity.current) { DRAG_THRESHOLD.toPx() }
    val dragOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    val riseScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val headId = photos.firstOrNull()?.id

    // 换卡(决策推进):残余偏移归零,新卡从 0.95 上浮到 1.0
    LaunchedEffect(headId) {
        dragOffset.snapTo(Offset.Zero)
        riseScale.snapTo(1f - CARD_SCALE_STEP)
        riseScale.animateTo(1f, animationSpec = tween(RISE_DURATION_MILLIS))
    }

    fun release() {
        val dx = dragOffset.value.x
        val dy = dragOffset.value.y
        when (dragOutcome(dx, dy, thresholdPx)) {
            DragOutcome.Keep -> scope.launch {
                dragOffset.animateTo(
                    Offset(dx * FLY_OUT_MULTIPLIER, dy),
                    spring(dampingRatio = FLY_OUT_DAMPING_RATIO, stiffness = FLY_OUT_STIFFNESS)
                )
                onKeep()
            }
            DragOutcome.Fallback -> {
                scope.launch { dragOffset.animateTo(Offset.Zero, spring()) }
                onFallback()
            }
            DragOutcome.SnapBack -> scope.launch { dragOffset.animateTo(Offset.Zero, spring()) }
        }
    }

    Box(modifier = modifier) {
        // 提示层:透明度与拖动联动,只在对应方向拖动时浮现(alpha ∝ |位移|/阈值)
        DirectionHint(
            label = stringResource(R.string.quicksort_hint_keep),
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            alpha = hintAlpha(-dragOffset.value.x, thresholdPx),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        DirectionHint(
            label = stringResource(R.string.quicksort_hint_delete),
            icon = Icons.Outlined.Delete,
            alpha = hintAlpha(-dragOffset.value.y, thresholdPx),
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 从后往前渲染:后写者在上,队首最后落笔、覆盖预览卡;depth = 距队首的层数
        val visible = photos.take(VISIBLE_CARDS)
        visible.asReversed().forEachIndexed { reversedIndex, photo ->
            key(photo.id) {
                StackedCard(
                    photo = photo,
                    depth = visible.size - 1 - reversedIndex,
                    riseScale = riseScale,
                    dragOffset = dragOffset,
                    onFallback = onFallback,
                    onRelease = ::release,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

/**
 * 堆叠里的一张卡:depth 0 = 队首(可拖,联动 [dragOffset] 与 [riseScale] 上浮),
 * 其余为静态预览层(scale / 纵移 / 透明度按层数递减)。
 */
@Composable
private fun StackedCard(
    photo: MediaPhoto,
    depth: Int,
    riseScale: Animatable<Float, *>,
    dragOffset: Animatable<Offset, *>,
    onRelease: () -> Unit,
    onFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isHead = depth == 0
    val scope = rememberCoroutineScope()
    Card(
        shape = RoundedCornerShape(CARD_CORNER_RADIUS),
        modifier = modifier
            .fillMaxWidth(CARD_WIDTH_FRACTION)
            .aspectRatio(CARD_ASPECT_RATIO)
            .graphicsLayer {
                val scale = if (isHead) riseScale.value else 1f - depth * CARD_SCALE_STEP
                scaleX = scale
                scaleY = scale
                alpha = 1f - depth * CARD_ALPHA_STEP
                translationY = depth * CARD_OFFSET_Y_STEP.toPx()
                if (isHead) {
                    translationX = dragOffset.value.x
                    translationY += dragOffset.value.y
                    rotationZ = dragOffset.value.x / ROTATION_DIVISOR
                }
            }
            .let { base ->
                if (isHead) {
                    base
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = onRelease,
                                onDragCancel = onRelease
                            ) { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragOffset.snapTo(dragOffset.value + dragAmount)
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { onFallback() })
                        }
                } else {
                    base
                }
            }
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/** 方向提示:图标 + 文字,按 [alpha] 浮现(默认隐藏);对齐由调用方经 modifier 指定。 */
@Composable
private fun DirectionHint(label: String, icon: ImageVector, alpha: Float, tint: Color, modifier: Modifier = Modifier) {
    if (alpha <= 0f) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(HINT_PADDING)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = alpha
                scaleY = alpha
            }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

/** 飞出目标:偏移放大到屏外(保留方向)。 */
private const val FLY_OUT_MULTIPLIER = 3f
