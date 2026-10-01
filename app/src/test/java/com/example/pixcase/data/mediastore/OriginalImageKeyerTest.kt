package com.example.pixcase.data.mediastore

import android.net.Uri
import coil3.request.Options
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class OriginalImageKeyerTest {
    private val rawUri = "content://media/external/images/media/42"

    @Test
    fun `produces a key so originals reach the memory cache`() {
        // Coil 的 newCacheKey 在没有任何 Keyer 命中时返回 null,而 key 为 null 的请求
        // 根本不进内存缓存 —— 缺了这个 Keyer,原图每次显示都要重新读盘解码。
        val uri = mockk<Uri>()
        every { uri.toString() } returns rawUri

        val key = OriginalImageKeyer().key(OriginalImage(uri), mockk<Options>(relaxed = true))

        assertNotNull(key)
        // 与缩略图那条按 Uri 字符串缓存的 key 必须不同,否则两张图会互相顶掉缓存。
        assertNotEquals(rawUri, key)
    }
}
