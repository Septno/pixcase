package com.example.pixcase.ui.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** 关键字高亮区间计算(大小写不敏感、不重叠、原字符串坐标系)。 */
class SearchHighlightTest {
    @Test
    fun `finds all case insensitive occurrences`() {
        // 第二次命中从下标 17 开始:"Bulk_250.jpg and " 共 17 个字符
        val ranges = highlightRanges("Bulk_250.jpg and bulk_259.png", "BULK_25")

        assertEquals(listOf(0..6, 17..23), ranges)
    }

    @Test
    fun `occurrences do not overlap`() {
        val ranges = highlightRanges("aaaa", "aa")

        assertEquals(listOf(0..1, 2..3), ranges)
    }

    @Test
    fun `blank query yields no ranges`() {
        assertEquals(emptyList<IntRange>(), highlightRanges("text", "  "))
        assertEquals(emptyList<IntRange>(), highlightRanges("text", ""))
    }

    @Test
    fun `query longer than text yields no ranges`() {
        assertEquals(emptyList<IntRange>(), highlightRanges("ab", "abc"))
    }

    @Test
    fun `no match yields no ranges`() {
        assertEquals(emptyList<IntRange>(), highlightRanges("photo.jpg", "xyz"))
    }
}
