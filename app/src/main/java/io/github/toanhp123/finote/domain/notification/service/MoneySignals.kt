package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.common.normalizeForMatch
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.text.Normalizer
import java.util.regex.Pattern

/** Bank-agnostic reading of a notification no rule matched. Labels are normalized (see `normalizeForMatch`). */
internal val BALANCE_LABELS = listOf("so du", "balance", "available", "han muc", "limit")
internal val TRANSACTION_LABELS =
    listOf(
        "thanh toan",
        "payment",
        "giao dich",
        "gd",
        "bien dong",
        "chuyen tien",
        "nhan tien",
        "converted amount",
        "so tien",
        "amount",
        "chi tieu",
    )
internal val FEE_WORDS = listOf("phi", "fee")

private const val MAX_VND_DIGITS = 15
private const val MASK_TAIL = 4

private fun wordRegex(words: List<String>) =
    Regex("(?<![\\p{L}\\p{N}])(?:${words.joinToString("|") { Pattern.quote(it) }})(?![\\p{L}\\p{N}])")

private val BALANCE_RE = wordRegex(BALANCE_LABELS)
private val FEE_RE = wordRegex(FEE_WORDS)
private val MONEY_WORD_RE = wordRegex(BALANCE_LABELS + TRANSACTION_LABELS)

// `bien dong so du` announces the change, so its `so du` is not a balance label for the amount that follows.
private val CHANGE_LABEL_RE = wordRegex(listOf("bien dong so du") + TRANSACTION_LABELS)

// 2+ mask chars (spaces allowed between), then 3-6 digits not followed by a digit, letter, % or k (a letter).
private val MASK_RE = Regex("(?:[*.x•] *){2,}(\\d{3,6})(?![\\d\\p{L}%])")

// A number with an optional attached sign, an optional 3-letter code or ₫ before it and an optional token after.
// shortcut: `d` before a number is not a currency (too loose once normalized), upgrade if a bank prints `đ150,000`.
private val AMOUNT_RE =
    Regex(
        "(?:(?<![\\p{L}\\p{N}])(?<sign>[+-]) ?)?(?:(?<![\\p{L}])(?<pre>[a-z]{3}|₫) ?)?" +
            "(?<num>\\d+(?:[.,]\\d+)*)(?: ?(?<post>vnd|d|₫)(?![\\p{L}\\d]))?",
    )
private val MINOR_UNIT_RE = Regex("[.,]\\d{2}$")
private val HORIZONTAL_RUN = Regex("\\h+")
private val MERCHANT_RE =
    Regex(
        "(?<!\\p{L})(?:payment at|tại|at) (.+?)(?: on |$)",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE),
    )

private class Amount(
    val sign: Char?,
    val digits: String,
    val isVnd: Boolean,
    val start: Int,
    val prefix: String,
)

private class Prepared(
    val text: String,
    val titleEnd: Int,
)

private fun prepare(
    title: String,
    content: String,
): Prepared {
    fun clean(s: String) = normalizeForMatch(s).replace("\r", "").replace(HORIZONTAL_RUN, " ")
    val head = clean(title)
    return Prepared(head + "\n" + clean(content), head.length)
}

/** Amounts that carry a currency; [Amount.prefix] is the same-line text from the previous one up to the digits. */
private fun currencyAmounts(text: String): List<Amount> {
    var prevEnd = 0
    return AMOUNT_RE
        .findAll(text)
        .filter { it.groups["pre"] != null || it.groups["post"] != null }
        .map { m ->
            val num = checkNotNull(m.groups["num"]) // `num` is not optional in AMOUNT_RE
            val numStart = num.range.first
            val lineStart = maxOf(text.lastIndexOf('\n', m.range.first) + 1, prevEnd)
            prevEnd = m.range.last + 1
            Amount(
                sign = m.groups["sign"]?.value?.single(),
                digits = num.value,
                isVnd = m.groups["pre"]?.value in setOf("vnd", "₫") || m.groups["post"] != null,
                start = m.range.first,
                prefix = text.substring(minOf(lineStart, numStart), numStart),
            )
        }.toList()
}

/** Money if a masked number shows, or a currency amount sits next to a balance or transaction word. */
fun looksLikeMoney(
    title: String,
    content: String,
): Boolean {
    val text = prepare(title, content).text
    return MASK_RE.containsMatchIn(text) ||
        (currencyAmounts(text).isNotEmpty() && MONEY_WORD_RE.containsMatchIn(text))
}

private fun parseVnd(number: String): Long? {
    val digits = number.replace(MINOR_UNIT_RE, "").filter(Char::isDigit)
    return if (digits.isEmpty() || digits.length > MAX_VND_DIGITS) null else digits.toLong().takeIf { it > 0 }
}

/** A transaction label sits before the amount with no balance label after it, and no fee word on the way. */
private fun isAfterTransactionLabel(prefix: String): Boolean {
    val label = CHANGE_LABEL_RE.findAll(prefix).lastOrNull() ?: return false
    return !BALANCE_RE.containsMatchIn(prefix.substring(label.range.last + 1)) && !FEE_RE.containsMatchIn(prefix)
}

/** Priority: signed VND, VND on the title line, VND after a transaction label, first VND not after a balance or fee. */
fun extractVndAmount(
    title: String,
    content: String,
): Long? {
    val p = prepare(title, content)
    val vnd = currencyAmounts(p.text).filter { it.isVnd }
    val pick =
        vnd.firstOrNull { it.sign != null }
            ?: vnd.firstOrNull { it.start < p.titleEnd }
            ?: vnd.firstOrNull { isAfterTransactionLabel(it.prefix) }
            ?: vnd.firstOrNull { !BALANCE_RE.containsMatchIn(it.prefix) && !FEE_RE.containsMatchIn(it.prefix) }
    return pick?.let { parseVnd(it.digits) }
}

/** Last 4 digits of the first masked number, or null when there is none or it shows fewer than 4 digits. */
fun extractMaskLast4(
    title: String,
    content: String,
): String? =
    MASK_RE
        .find(prepare(title, content).text)
        ?.groupValues
        ?.get(1)
        ?.takeIf { it.length >= MASK_TAIL }
        ?.takeLast(MASK_TAIL)

/** Direction from a `+` or `-` attached to a currency amount; null when no such sign. */
fun extractSignedType(
    title: String,
    content: String,
): TransactionType? =
    when (currencyAmounts(prepare(title, content).text).firstNotNullOfOrNull { it.sign }) {
        '-' -> TransactionType.EXPENSE
        '+' -> TransactionType.INCOME
        else -> null
    }

/** Text after `payment at` / `tại` / `at` up to ` on ` or end of line, case kept. */
fun extractMerchant(
    title: String,
    content: String,
): String? {
    val text =
        Normalizer
            .normalize("$title\n$content", Normalizer.Form.NFC)
            .replace("\r", "")
            .replace(HORIZONTAL_RUN, " ")
    return MERCHANT_RE
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}
