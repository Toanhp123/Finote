package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.common.containsPhrase
import java.security.MessageDigest

/** The first of [keywords], in list order, that appears in [text] as a whole word or phrase. See [containsPhrase]. */
fun firstMatchingKeyword(
    text: String,
    keywords: List<String>,
): String? = keywords.firstOrNull { containsPhrase(text, it) }

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
