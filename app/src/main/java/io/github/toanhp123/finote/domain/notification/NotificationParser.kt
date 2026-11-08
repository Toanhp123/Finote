package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.notification.model.ParseResult
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

private val GROUP_NAME = Regex("\\(\\?<([a-zA-Z][a-zA-Z0-9]*)>")
private val DECIMAL_TAIL = Regex("[.,]\\d{2}$")

/**
 * Runs the active [rules] over title and content, highest priority first (then by id).
 * The first rule that is not rejected wins. A pattern that does not compile is skipped.
 */
fun parseNotification(
    title: String,
    content: String,
    rules: List<ParserRule>,
): ParseResult {
    val text = title + "\n" + content
    return rules
        .filter { it.isActive }
        .sortedWith(compareByDescending<ParserRule> { it.priority }.thenBy { it.id })
        .firstNotNullOfOrNull { applyRule(it, text) }
        ?: ParseResult.NoMatch
}

/** Names of the `(?<name>...)` groups a pattern declares, by text scan (it can also see names inside escapes). */
fun regexGroupNames(pattern: String): Set<String> = GROUP_NAME.findAll(pattern).map { it.groupValues[1] }.toSet()

private fun applyRule(
    rule: ParserRule,
    text: String,
): ParseResult.Parsed? {
    val match = runCatching { Regex(rule.regexPattern) }.getOrNull()?.find(text) ?: return null

    val names = regexGroupNames(rule.regexPattern)

    // The scan can also see a name that is only text (escaped, or in a character class); asking for it throws.
    fun group(name: String): String? =
        if (name in names) runCatching { match.groups[name]?.value }.getOrNull() else null

    val type = resolveType(group("sign"), rule.fallbackTransactionType)
    val amount = group("amount")?.let(::parseMoney)?.takeIf { it > 0 }
    return if (type == null || amount == null) {
        null
    } else {
        ParseResult.Parsed(
            rule.id,
            ParsedFields(
                amount = amount,
                type = type,
                accountMask = group("mask").cleaned(),
                postBalance = group("balance")?.let(::parseMoney),
                merchant = group("merchant").cleaned(),
                note = group("note").cleaned(),
            ),
        )
    }
}

private fun String?.cleaned(): String? = this?.trim()?.ifEmpty { null }

// A sign group that matched anything but + or - rejects the rule; no sign uses the fallback.
private fun resolveType(
    sign: String?,
    fallback: TransactionType?,
): TransactionType? =
    when (sign?.trim()) {
        null -> fallback
        "+" -> TransactionType.INCOME
        "-" -> TransactionType.EXPENSE
        else -> null
    }

/** Reads a VND amount such as `1.500.000đ` or `1,500,000.00 VND`. Null when there is no digit or it overflows. */
fun parseMoney(raw: String): Long? {
    val kept = raw.filter { it.isDigit() || it == '.' || it == ',' }
    // VND groups are three digits, so a separator plus two digits at the end is a decimal part.
    return kept
        .replace(DECIMAL_TAIL, "")
        .filter(Char::isDigit)
        .ifEmpty { null }
        ?.toLongOrNull()
}
