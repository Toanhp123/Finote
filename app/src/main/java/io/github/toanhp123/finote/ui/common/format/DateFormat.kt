package io.github.toanhp123.finote.ui.common.format

import android.icu.text.DateTimePatternGenerator
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private const val DAY_SKELETON = "MMddEEE"
private const val DAY_YEAR_SKELETON = "yMMddEEE"
private const val MONTH_YEAR_SKELETON = "yMMMM"

/** Weekday and day for [locale], with the year unless [date] is in the year of [today]. The locale sets the order. */
fun formatDayDate(
    date: LocalDate,
    today: LocalDate,
    locale: Locale,
): String {
    val skeleton = if (date.year == today.year) DAY_SKELETON else DAY_YEAR_SKELETON
    val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

/** Month and year for [locale], such as `November 2026`. The locale sets the order. */
fun formatMonthYear(
    month: YearMonth,
    locale: Locale,
): String {
    val pattern = DateTimePatternGenerator.getInstance(locale).getBestPattern(MONTH_YEAR_SKELETON)
    return DateTimeFormatter.ofPattern(pattern, locale).format(month)
}

/** A medium date such as `Nov 5, 2026`, in [locale]. */
fun formatMediumDate(
    date: LocalDate,
    locale: Locale,
): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

/** Short date and time of [epochMillis] in [zone], in [locale]. */
fun formatShortDateTime(
    epochMillis: Long,
    zone: ZoneId,
    locale: Locale,
): String =
    DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.SHORT)
        .withLocale(locale)
        .withZone(zone)
        .format(Instant.ofEpochMilli(epochMillis))
