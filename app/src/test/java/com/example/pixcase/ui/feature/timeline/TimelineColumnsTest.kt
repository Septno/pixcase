package com.example.pixcase.ui.feature.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 捏合变列数纯逻辑的覆盖:列数上下限由屏幕宽度反推、zoom 累积分步换算。
 * 纯 JVM 函数,不依赖 Android 类。
 */
class TimelineColumnsTest {

    // ---- maxColumnsForWidth ----

    @Test
    fun `typical phone width allows 6 columns at minimum readable cell size`() {
        // 412dp - 4dp 留白 = 408dp;408 / 64 = 6.375 → 6
        assertEquals(6, maxColumnsForWidth(412))
    }

    @Test
    fun `narrow width allows fewer columns`() {
        assertEquals(3, maxColumnsForWidth(200))
    }

    @Test
    fun `very narrow width clamps to at least one column`() {
        // 60dp 宽连一个 64dp 格子都放不下,下限 1
        assertEquals(1, maxColumnsForWidth(60))
    }

    @Test
    fun `boundary width fits exactly the division result`() {
        // 68 - 4 = 64 → 恰好 1 个
        assertEquals(1, maxColumnsForWidth(68))
        // 132 - 4 = 128 → 恰好 2 个
        assertEquals(2, maxColumnsForWidth(132))
    }

    // ---- clampColumns ----

    @Test
    fun `clamp keeps value inside width derived range`() {
        assertEquals(3, clampColumns(3, 412))
        assertEquals(1, clampColumns(0, 412))
        assertEquals(6, clampColumns(99, 412))
    }

    @Test
    fun `clamp uses current screen width not persisted one`() {
        // 平板上捏到 8 列的偏好,到窄屏上显示时被夹回宽度允许的上限
        assertEquals(3, clampColumns(8, 200))
    }

    // ---- accumulateZoom ----

    @Test
    fun `zoom out beyond step reduces columns by one and consumes the step`() {
        // 1 * 1.5 = 1.5 ≥ 1.25 → 消费一档,剩 1.2;步数 -1(张开双指,列数变少)
        assertEquals(1.2f to -1, accumulateZoom(1f, 1.5f))
    }

    @Test
    fun `zoom in beyond step adds columns by one`() {
        // 1 * 0.6 = 0.6:0.8 的收拢一步约是 0.8,0.6 ≈ 0.8² → 跨两档,
        // 残余补回 (0.8, 1] 区间(收拢双指,列数变多)
        val (acc, steps) = accumulateZoom(1f, 0.6f)
        assertEquals(2, steps)
        org.junit.Assert.assertTrue("residual $acc not in (0.8, 1]", acc in 0.8f..1f)
    }

    @Test
    fun `small zoom within step changes nothing`() {
        assertEquals(1.1f to 0, accumulateZoom(1f, 1.1f))
        assertEquals(0.9f to 0, accumulateZoom(1f, 0.9f))
    }

    @Test
    fun `accumulated zoom crosses multiple steps in one event`() {
        // 1.2 * 1.5 = 1.8 → 跨两档(1.8/1.25=1.44 ≥ 1.25 再除一次 → 1.152),步数 -2;
        // 残余带 float 误差,用差值断言
        val (acc, steps) = accumulateZoom(1.2f, 1.5f)
        assertEquals(-2, steps)
        assertEquals(1.152f, acc, 0.001f)
    }

    @Test
    fun `residual accumulator from previous event carries over`() {
        // 第一次 1.2(未跨档),第二次再乘 1.2 = 1.44 ≥ 1.25 → 跨一档剩 1.152
        val (acc, steps) = accumulateZoom(1.2f, 1.2f)
        assertEquals(-1, steps)
        assertEquals(1.152f, acc, 0.001f)
    }

    @Test
    fun `no infinite loop on extreme zoom`() {
        // 一次巨大的 zoom 也要正常终止并按档数计算;残余收敛到 [1, 1.25)
        val (acc, steps) = accumulateZoom(1f, 1000f)
        assertTrue(steps < 0)
        assertTrue("residual $acc not in [1, 1.25)", acc >= 1f && acc < ZOOM_STEP)
    }
}
