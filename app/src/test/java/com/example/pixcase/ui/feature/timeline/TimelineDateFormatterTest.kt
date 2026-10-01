package com.example.pixcase.ui.feature.timeline

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineDateFormatterTest {
    private val locale = Locale.SIMPLIFIED_CHINESE
    private val today = LocalDate.of(2026, 5, 10)

    @Test
    fun `today maps to Today label`() {
        assertEquals(DateHeaderLabel.Today, dateHeaderLabel(today, today, locale))
    }

    @Test
    fun `previous day maps to Yesterday label`() {
        assertEquals(DateHeaderLabel.Yesterday, dateHeaderLabel(today.minusDays(1), today, locale))
    }

    @Test
    fun `yesterday across a month boundary still maps to Yesterday`() {
        val firstOfMonth = LocalDate.of(2026, 5, 1)

        assertEquals(
            DateHeaderLabel.Yesterday,
            dateHeaderLabel(LocalDate.of(2026, 4, 30), firstOfMonth, locale)
        )
    }

    @Test
    fun `older date maps to localized exact text`() {
        val label = dateHeaderLabel(LocalDate.of(2026, 4, 20), today, locale)

        assertTrue("expected Exact, got $label", label is DateHeaderLabel.Exact)
        // 不断言具体格式:输出随 JDK / CLDR 版本浮动,这里只验证落到目标年份
        assertTrue((label as DateHeaderLabel.Exact).text.contains("2026"))
    }

    @Test
    fun `future dates are not treated as Today`() {
        val label = dateHeaderLabel(today.plusDays(1), today, locale)

        assertTrue("expected Exact, got $label", label is DateHeaderLabel.Exact)
    }
}
