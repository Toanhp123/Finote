package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

private const val MIN_KEYWORD_LENGTH = 3

private val REAL_MONEY_STATUSES =
    setOf(
        NotificationStatus.PENDING,
        NotificationStatus.CONFIRMED,
        NotificationStatus.DEDUPLICATED,
        NotificationStatus.PAIRED_TRANSFER,
        NotificationStatus.IGNORED_ACCOUNT,
    )

// Symbols (emoji), the joiner and variation selector that glue them, and the skin tone modifiers.
private val EMOJI_PARTS = Regex("[\\p{So}\\u200D\\uFE0F\\x{1F3FB}-\\x{1F3FF}]")
private val EDGE_PUNCTUATION = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")
private val SPACE_RUN = Regex("[\\s\\p{Z}]+")

/**
 * Why [keyword] may not be taught as an ad from [selected], or null when it may. Checks run in this order:
 * length, in the selected log, the bank's own name, a real money log of the package, another unparsed log that
 * looks like money, then [existingKeywords] (already normalized). [packageLogs] is every stored log of the package.
 * The identical copies of the selected text are the same inbox item, so they are not "another" money item.
 */
fun checkAdKeyword(
    keyword: String,
    selected: NotificationLog,
    packageLogs: List<NotificationLog>,
    bankName: String,
    existingKeywords: List<String> = emptyList(),
): AdKeywordRefusal? {
    val normalized = normalizeKeyword(keyword)
    return when {
        normalized.length < MIN_KEYWORD_LENGTH -> {
            AdKeywordRefusal.TOO_SHORT
        }

        !selected.contains(keyword) -> {
            AdKeywordRefusal.NOT_IN_SELECTED
        }

        normalized == normalizeKeyword(bankName) -> {
            AdKeywordRefusal.BANK_NAME
        }

        packageLogs.any { it.isRealMoney() && it.contains(keyword) } -> {
            AdKeywordRefusal.IN_REAL_TRANSACTION
        }

        packageLogs.any { it.isOtherMoneyItem(selected) && it.contains(keyword) } -> {
            AdKeywordRefusal.IN_OTHER_MONEY_ITEM
        }

        normalized in existingKeywords -> {
            AdKeywordRefusal.DUPLICATE
        }

        else -> {
            null
        }
    }
}

/** The `FAILED` / `no_rule_matched` logs of [packageLogs] that [keyword] matches: the ones it would hide. */
fun adKeywordMatches(
    keyword: String,
    packageLogs: List<NotificationLog>,
): List<NotificationLog> = packageLogs.filter { it.isUnparsed() && it.contains(keyword) }

/** How many inbox items other than [selected] [keyword] would hide; identical copies are one item. */
fun countOtherHiddenItems(
    keyword: String,
    selected: NotificationLog,
    packageLogs: List<NotificationLog>,
): Int {
    val texts = adKeywordMatches(keyword, packageLogs).map { it.title to it.content }.toSet()
    return (texts - (selected.title to selected.content)).size
}

/** [title] without emoji and without leading or trailing punctuation, spaces collapsed: the sheet's first guess. */
fun suggestAdKeyword(title: String): String =
    title
        .replace(EMOJI_PARTS, "")
        .replace(SPACE_RUN, " ")
        .replace(EDGE_PUNCTUATION, "")
        .trim()

private fun NotificationLog.contains(keyword: String) =
    firstMatchingKeyword("$title\n$content", listOf(keyword)) != null

private fun NotificationLog.isUnparsed() = status == NotificationStatus.FAILED && failureReason == FAIL_NO_RULE_MATCHED

private fun NotificationLog.isRealMoney() =
    status in REAL_MONEY_STATUSES || (status == NotificationStatus.FAILED && failureReason != FAIL_NO_RULE_MATCHED)

private fun NotificationLog.isOtherMoneyItem(selected: NotificationLog) =
    isUnparsed() &&
        (title != selected.title || content != selected.content) &&
        looksLikeMoney(title, content)
