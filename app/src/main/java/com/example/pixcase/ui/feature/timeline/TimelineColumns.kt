package com.example.pixcase.ui.feature.timeline

/**
 * 捏合变列数的纯逻辑:列数上下限、捏合累积换算。
 *
 * 抽成无状态函数是为了在 JVM 单测覆盖(见 TimelineColumnsTest),
 * PhotoGrid 只负责把指针事件喂进来、把结果应用到 LazyGrid。
 */
private const val MIN_READABLE_CELL_DP = 64
private const val GRID_SPACING_DP = 4

/** 绝对下限:放大到极限时一行一张。 */
internal const val MIN_COLUMNS = 1

/** 绝对上限:防止异常窄屏算出离谱值;正常由屏幕宽度反推约束。 */
internal const val MAX_ABSOLUTE_COLUMNS = 12

/** 每跨过一档捏合幅度,列数变化一步;1.25 表示捏合 25% 变一档,天然节流重解码。 */
internal const val ZOOM_STEP = 1.25f

/**
 * 由屏幕宽度反推列数上限:保证每格边长不小于「缩略图刚好能辨认」的可读边长。
 * [screenWidthDp] 减去网格两侧留白后,能塞下几个 64dp 的格子,上限就是几。
 */
internal fun maxColumnsForWidth(screenWidthDp: Int): Int =
    ((screenWidthDp - GRID_SPACING_DP) / MIN_READABLE_CELL_DP).coerceAtLeast(MIN_COLUMNS)

/** 把用户期望的列数夹到 [MIN_COLUMNS, 由宽度反推的上限] 区间内。 */
internal fun clampColumns(requested: Int, screenWidthDp: Int): Int =
    requested.coerceIn(MIN_COLUMNS, maxColumnsForWidth(screenWidthDp))

/**
 * 把一次指针事件的 zoom 系数累积进 [accumulator],返回(新累积值, 本事件应变化的步数)。
 *
 * 步数为负 = 减列(双指张开,格子变大);为正 = 加列(双指收拢,格子变小)。
 * 累积值跨过一档 [ZOOM_STEP] 就消费掉一档、列数变一步 —— 连续捏合被切成离散的一步步,
 * Coil 不会因为连续微小的列数抖动而连发解码。
 */
internal fun accumulateZoom(accumulator: Float, zoom: Float): Pair<Float, Int> {
    var acc = accumulator * zoom
    var steps = 0
    while (acc >= ZOOM_STEP) {
        acc /= ZOOM_STEP
        steps--
    }
    while (acc <= 1 / ZOOM_STEP) {
        acc *= ZOOM_STEP
        steps++
    }
    return acc to steps
}
