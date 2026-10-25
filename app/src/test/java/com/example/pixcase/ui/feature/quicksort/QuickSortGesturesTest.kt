package com.example.pixcase.ui.feature.quicksort

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 卡片手势的阈值判定与提示层透明度(计划 § 3.5.6 测试项「提示层显隐阈值」):
 * 左划超阈值 = 保留,右划 / 纵向超阈值 = 兜底,未过阈值弹回;
 * 透明度 = |位移| / 阈值,夹在 0..1,反向位移不点亮,非法阈值安全回退。
 */
class QuickSortGesturesTest {
    private val threshold = 500f

    @Test
    fun `left swipe beyond threshold keeps`() {
        assertEquals(DragOutcome.Keep, dragOutcome(dx = -threshold - 1f, dy = 0f, thresholdPx = threshold))
        assertEquals(DragOutcome.Keep, dragOutcome(dx = -2000f, dy = 300f, thresholdPx = threshold))
    }

    @Test
    fun `right swipe beyond threshold opens fallback`() {
        assertEquals(DragOutcome.Fallback, dragOutcome(dx = threshold + 1f, dy = 0f, thresholdPx = threshold))
    }

    @Test
    fun `vertical swipe beyond threshold opens fallback`() {
        // 上划是删除方向(执行语义属删除任务),当前同样落兜底
        assertEquals(DragOutcome.Fallback, dragOutcome(dx = 0f, dy = -threshold - 1f, thresholdPx = threshold))
        assertEquals(DragOutcome.Fallback, dragOutcome(dx = 0f, dy = threshold + 1f, thresholdPx = threshold))
    }

    @Test
    fun `small drags snap back`() {
        assertEquals(DragOutcome.SnapBack, dragOutcome(dx = 0f, dy = 0f, thresholdPx = threshold))
        assertEquals(DragOutcome.SnapBack, dragOutcome(dx = threshold - 1f, dy = 0f, thresholdPx = threshold))
        assertEquals(DragOutcome.SnapBack, dragOutcome(dx = -threshold + 1f, dy = 0f, thresholdPx = threshold))
    }

    @Test
    fun `exact threshold counts as beyond for vertical but not left`() {
        // dx == -threshold 不满足「dx < -threshold」→ 弹回;纵向 |dy| == threshold 满足「> 」? 不满足 → 也弹回
        assertEquals(DragOutcome.SnapBack, dragOutcome(dx = -threshold, dy = 0f, thresholdPx = threshold))
        assertEquals(DragOutcome.SnapBack, dragOutcome(dx = 0f, dy = threshold, thresholdPx = threshold))
    }

    @Test
    fun `hint alpha scales with displacement ratio`() {
        assertEquals(0f, hintAlpha(0f, threshold))
        assertEquals(0.5f, hintAlpha(threshold / 2f, threshold))
        assertEquals(1f, hintAlpha(threshold, threshold))
    }

    @Test
    fun `hint alpha saturates beyond threshold`() {
        assertEquals(1f, hintAlpha(threshold * 3f, threshold))
        assertEquals(1f, hintAlpha(-threshold * 3f, threshold))
    }

    @Test
    fun `hint alpha is direction aware when fed signed displacement`() {
        // 提示层喂的是「反向位移」(如 -dx):左划时 -dx > 0 点亮,右划时 -dx < 0 不亮
        assertEquals(0.5f, hintAlpha(-threshold / 2f, threshold))
    }

    @Test
    fun `hint alpha with invalid threshold stays hidden`() {
        assertEquals(0f, hintAlpha(threshold, 0f))
        assertEquals(0f, hintAlpha(threshold, -1f))
    }
}
