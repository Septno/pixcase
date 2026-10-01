package com.example.pixcase.ui.feature.timeline

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * 日期表头文案。
 *
 * 「今天 / 昨天」交给调用方用字符串资源渲染,其余用 locale 感知的本地化日期。
 * 拆成 sealed 结果是为了让「哪一天算今天/昨天」这段判断脱离 Android 资源也能单测。
 */
sealed interface DateHeaderLabel {
    data object Today : DateHeaderLabel

    data object Yesterday : DateHeaderLabel

    data class Exact(val text: String) : DateHeaderLabel
}

internal fun dateHeaderLabel(date: LocalDate, today: LocalDate, locale: Locale): DateHeaderLabel = when (date) {
    today -> DateHeaderLabel.Today
    today.minusDays(1) -> DateHeaderLabel.Yesterday
    else -> DateHeaderLabel.Exact(formatDate(date, locale))
}

private fun formatDate(date: LocalDate, locale: Locale): String = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
    .withLocale(locale)
    .format(date)
