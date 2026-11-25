package io.github.toanhp123.finote.domain.common

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")

// Horizontal only, so a keyword cannot match across the newline between title and content. Covers NBSP.
private val HORIZONTAL_SPACE_RUN = Regex("[\\h\\p{Zs}]+")

/** Lowercase, no Vietnamese accents, `đ` → `d`. NFD first so composed and decomposed input compare equal. */
fun normalizeForMatch(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(DIACRITICS, "")
        .replace('đ', 'd')
        .replace('Đ', 'd')
        .lowercase(Locale.ROOT)

/** [normalizeForMatch] with every horizontal whitespace run (NBSP included) as one space, trimmed. Newlines stay. */
fun normalizeKeyword(text: String): String = normalizeForMatch(text).replace(HORIZONTAL_SPACE_RUN, " ").trim()

/**
 * True when [keyword] appears in [text] as a whole word or phrase; both are raw and normalized here.
 * Whole word, because a bare `contains` finds `otp` in `hotpot` and `uu dai` in `luu dai`. Digits may follow
 * the keyword (`OTP123456`) but not precede it or letters follow it. A blank keyword never matches.
 */
fun containsPhrase(
    text: String,
    keyword: String,
): Boolean {
    val needle = normalizeKeyword(keyword)
    return needle.isNotEmpty() &&
        Regex("(?<![\\p{L}\\p{N}])${Pattern.quote(needle)}(?!\\p{L})").containsMatchIn(normalizeKeyword(text))
}
