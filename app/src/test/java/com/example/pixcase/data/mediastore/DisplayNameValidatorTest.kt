package com.example.pixcase.data.mediastore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 重命名纯逻辑覆盖:主体名拆取、新名拼装(扩展名保护)、非法输入校验。
 */
class DisplayNameValidatorTest {

    // ---- baseNameOf ----

    @Test
    fun `baseNameOf strips the last extension`() {
        assertEquals("IMG_0001", baseNameOf("IMG_0001.jpg"))
        assertEquals("照片", baseNameOf("照片.png"))
    }

    @Test
    fun `baseNameOf keeps dots inside the base`() {
        assertEquals("2024.01.02", baseNameOf("2024.01.02.jpg"))
    }

    @Test
    fun `baseNameOf returns whole name when no or leading dot`() {
        assertEquals("noext", baseNameOf("noext"))
        // 点在首位:".jpg" 的主体为空,视为无扩展名的普通名字
        assertEquals(".jpg", baseNameOf(".jpg"))
    }

    // ---- buildDisplayName ----

    @Test
    fun `buildDisplayName reattaches original extension`() {
        assertEquals("holiday.png", buildDisplayName("holiday", "IMG.png"))
        assertEquals("holiday.jpeg", buildDisplayName(" holiday ", "IMG.jpeg"))
    }

    @Test
    fun `buildDisplayName keeps no extension when original had none`() {
        assertEquals("holiday", buildDisplayName("holiday", "noext"))
    }

    @Test
    fun `buildDisplayName never lets user change the extension`() {
        // 主体名里带点也不会拼出双扩展:点属于主体,扩展名永远来自原名
        assertEquals("a.b.jpg", buildDisplayName("a.b", "IMG.jpg"))
    }

    // ---- isValidDisplayNameBase ----

    @Test
    fun `valid base names pass`() {
        assertTrue(isValidDisplayNameBase("holiday 2024"))
        assertTrue(isValidDisplayNameBase("  trimmed  "))
    }

    @Test
    fun `blank base name is invalid`() {
        assertFalse(isValidDisplayNameBase(""))
        assertFalse(isValidDisplayNameBase("   "))
    }

    @Test
    fun `illegal characters are rejected`() {
        ILLEGAL_CASES.forEach { case ->
            assertFalse("expected invalid: $case", isValidDisplayNameBase(case))
        }
    }

    @Test
    fun `overlong base name is invalid`() {
        assertFalse(isValidDisplayNameBase("a".repeat(101)))
        assertTrue(isValidDisplayNameBase("a".repeat(100)))
    }

    private companion object {
        val ILLEGAL_CASES = listOf("a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b")
    }
}
