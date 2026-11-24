package io.github.toanhp123.finote.domain.notification.service

import java.security.MessageDigest
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
 * The first of [keywords], in list order, that appears in [text] as a whole word or phrase.
 * Whole word, because a bare `contains` finds `otp` in `hotpot` and `uu dai` in `luu dai`. Digits may follow
 * the keyword (`OTP123456`) but not precede it or letters follow it.
 */
fun firstMatchingKeyword(
    text: String,
    keywords: List<String>,
): String? {
    val normalized = normalizeKeyword(text)
    return keywords.firstOrNull { keyword ->
        val needle = normalizeKeyword(keyword)
        needle.isNotEmpty() &&
            Regex("(?<![\\p{L}\\p{N}])${Pattern.quote(needle)}(?!\\p{L})").containsMatchIn(normalized)
    }
}

/** SHA-256 hex of the fields joined by NUL, so moving text between fields changes the hash. */
fun contentHash(
    packageName: String,
    postTime: Long,
    title: String,
    content: String,
): String {
    val input = listOf(packageName, postTime.toString(), title, content).joinToString("\u0000")
    return MessageDigest
        .getInstance("SHA-256")
        .digest(input.toByteArray(Charsets.UTF_8))
        .toHexString()
}

/** Full notification text: big text, else the non-blank inbox lines, else the short text. Null when all are blank. */
fun notificationContent(
    bigText: CharSequence?,
    textLines: Array<CharSequence>?,
    text: CharSequence?,
): String? {
    val lines = textLines.orEmpty().filter { it.isNotBlank() }.joinToString("\n")
    return listOf(bigText?.toString(), lines, text?.toString())
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
}

/** Notifications that carry a code or a secret. They are dropped for every package, whatever the rules say. */
val SECRET_KEYWORDS: List<String> = listOf("OTP", "SmartOTP", "mã xác thực")
