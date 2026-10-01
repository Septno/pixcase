package com.example.pixcase.data.mediastore

import coil3.size.Dimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖 [MediaStoreThumbnailFetcher] 里的纯函数。
 *
 * Fetcher 本体依赖 Bitmap / CancellationSignal / ContentResolver,在无 Robolectric 的
 * JVM 单测里全是 stub,因此判断逻辑全部外提为纯函数,这里只测纯函数。
 */
class MediaStoreThumbnailFetcherTest {

    @Test
    fun `resolved dimension keeps requested pixels`() {
        assertEquals(360, thumbnailPx(Dimension.Pixels(360), fallbackPx = 512))
    }

    @Test
    fun `undefined dimension falls back to default`() {
        assertEquals(DEFAULT_THUMBNAIL_PX, thumbnailPx(Dimension.Undefined))
    }

    @Test
    fun `undefined dimension uses provided fallback`() {
        assertEquals(128, thumbnailPx(Dimension.Undefined, fallbackPx = 128))
    }

    @Test
    fun `native thumbnail path requires API 29`() {
        assertFalse(useNativeThumbnail(26))
        assertFalse(useNativeThumbnail(28))
        assertTrue(useNativeThumbnail(29))
        assertTrue(useNativeThumbnail(36))
    }
}
