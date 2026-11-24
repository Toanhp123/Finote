package io.github.toanhp123.finote.ui.settings.accountedit.logic

private const val MAX_CHARS = 15

/**
 * The start balance a typed text means: digits, plus one leading minus (a stored provisional start can be negative).
 * Null is an empty or unreadable field. The text is cut at [MAX_CHARS] so it cannot overflow `Long`.
 */
fun parseStartBalance(text: String): Long? =
    text
        .filterIndexed { index, char -> char.isDigit() || (index == 0 && char == '-') }
        .take(MAX_CHARS)
        .toLongOrNull()
