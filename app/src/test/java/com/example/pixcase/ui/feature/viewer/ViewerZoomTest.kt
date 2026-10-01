package com.example.pixcase.ui.feature.viewer

import org.junit.Assert.assertEquals
import org.junit.Test

/** 手势本身只能真机验证,这里覆盖它依赖的边界计算。 */
class ViewerZoomTest {
    private val delta = 1e-4f

    @Test
    fun `scale clamps into the allowed range`() {
        assertEquals(MIN_SCALE, clampScale(0.2f), delta)
        assertEquals(2.5f, clampScale(2.5f), delta)
        assertEquals(MAX_SCALE, clampScale(50f), delta)
    }

    @Test
    fun `no panning when not zoomed`() {
        // 1x 时图片正好适配视口,任何位移都会把图片拖出去露出空白。
        assertEquals(0f, clampOffset(offset = 120f, scale = 1f, containerSize = 1000f), delta)
        assertEquals(0f, clampOffset(offset = -120f, scale = 1f, containerSize = 1000f), delta)
    }

    @Test
    fun `offset is limited to half the overflow`() {
        // 放大 2 倍、视口 1000px:总溢出 1000px,分摊到两侧各 500px。
        assertEquals(500f, clampOffset(offset = 9999f, scale = 2f, containerSize = 1000f), delta)
        assertEquals(-500f, clampOffset(offset = -9999f, scale = 2f, containerSize = 1000f), delta)
    }

    @Test
    fun `offset inside the limit is kept`() {
        assertEquals(300f, clampOffset(offset = 300f, scale = 2f, containerSize = 1000f), delta)
    }

    @Test
    fun `scale below one behaves like no zoom`() {
        assertEquals(0f, clampOffset(offset = 50f, scale = 0.5f, containerSize = 1000f), delta)
    }
}
