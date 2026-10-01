package com.example.pixcase.ui.feature.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** 缩放下限:1x 即原始适配尺寸,不允许缩到比屏幕适配更小。 */
internal const val MIN_SCALE = 1f

/** 缩放上限。手机屏幕上超过 5x 已经只能看到像素块,再大没有意义。 */
internal const val MAX_SCALE = 5f

/** 把缩放系数钳进 [MIN_SCALE, MAX_SCALE]。 */
internal fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

/**
 * 把位移钳在图片可以被拖动的范围内。
 *
 * 放大 [scale] 倍后,图片总尺寸超出视口 `(scale - 1) * containerSize`,分摊到两侧
 * 每侧可移动 `(scale - 1) * containerSize / 2`。超出这个量就会把图片拖出屏幕露出空白。
 *
 * [containerSize] 用视口宽 / 高分别传入;未放大([scale] <= 1)时上限为 0,位移被钳回原点。
 * 抽成纯函数:手势本身只能真机验证,但这条边界计算可以单测。
 */
internal fun clampOffset(offset: Float, scale: Float, containerSize: Float): Float {
    val limit = ((scale - 1f).coerceAtLeast(0f) * containerSize) / 2f
    return offset.coerceIn(-limit, limit)
}

/**
 * 查看器的缩放与位移状态。
 *
 * 同一时刻只有一页可见,所以整个查看器共用一份就够 —— 也正因如此,
 * 返回键才能在放大状态下先复位缩放,而不是直接退出查看器。
 */
@Stable
internal class ViewerZoomState {
    var scale by mutableFloatStateOf(MIN_SCALE)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    /**
     * 视口尺寸。只在手势回调里读、不参与重组,所以是普通字段而不是 snapshot state ——
     * 做成 state 只会让每页尺寸变化时触发一轮没有产出的重组。
     */
    var containerSize: IntSize = IntSize.Zero

    val isZoomed: Boolean get() = scale > MIN_SCALE

    /** 应用一次手势:[zoomChange] 是本次捏合的倍数,[panChange] 是本次拖动的像素位移。 */
    fun transform(zoomChange: Float, panChange: Offset) {
        val newScale = clampScale(scale * zoomChange)
        scale = newScale
        offset = Offset(
            x = clampOffset(offset.x + panChange.x, newScale, containerSize.width.toFloat()),
            y = clampOffset(offset.y + panChange.y, newScale, containerSize.height.toFloat())
        )
    }

    fun reset() {
        scale = MIN_SCALE
        offset = Offset.Zero
    }
}

/**
 * 建立缩放状态,并接上「放大状态下按返回键先复位缩放」这条规则。
 *
 * 不需要「翻页后复位」:放大时翻不动页(分页器已被禁用),未放大时 [ViewerZoomState.reset]
 * 本来就是空操作。
 */
@Composable
internal fun rememberViewerZoomState(): ViewerZoomState {
    val state = remember { ViewerZoomState() }
    BackHandler(enabled = state.isZoomed) { state.reset() }
    return state
}
