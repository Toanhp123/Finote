package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.notification.model.NotificationLog

/**
 * Replaces every ASCII digit with `1` and leaves all other characters alone, so a copied sample keeps its
 * shape but leaks no amount, balance or account number. Not `isDigit()`: it also matches non-ASCII digits,
 * which bank texts never use as numbers.
 */
fun maskDigits(text: String): String = text.map { if (it in '0'..'9') '1' else it }.joinToString("")

fun NotificationLog.masked(): NotificationLog = copy(title = maskDigits(title), content = maskDigits(content))
