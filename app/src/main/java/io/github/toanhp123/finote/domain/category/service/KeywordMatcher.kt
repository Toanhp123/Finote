package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.common.containsPhrase
import io.github.toanhp123.finote.domain.common.normalizeForMatch
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.util.regex.PatternSyntaxException

private const val MAX_GUESS = 30
private val WHITESPACE = Regex("\\s+")
private val TRANSFER_WORDS = setOf("tien", "khoan")

// User rules first; a longer keyword is more specific (`grab food` before `grab`), whatever its match type.
private val RULE_ORDER =
    compareBy<KeywordRule>(
        { !it.isUserDefined },
        { -it.normalizedKeyword.length },
        { -it.priority },
        { -it.updatedAt },
        { it.id },
    )

/**
 * The first active rule that matches [merchant] or [note], each field on its own, whose category is live, shown and
 * of the row's type. `null` for a type that carries no category (transfer, adjustment, refund).
 */
fun classify(
    merchant: String?,
    note: String?,
    type: TransactionType,
    rules: List<KeywordRule>,
    categories: Map<String, Category>,
): RuleMatch? {
    val categoryType =
        when (type) {
            TransactionType.EXPENSE -> CategoryType.EXPENSE
            TransactionType.INCOME -> CategoryType.INCOME
            else -> return null
        }
    val fields = listOfNotNull(merchant, note).filter { it.isNotBlank() }
    val rule =
        rules
            .filter { it.isActive && categories[it.categoryId].isUsable(categoryType) }
            .sortedWith(RULE_ORDER)
            .firstOrNull { rule -> fields.any(rule::matches) }
    return rule?.let { RuleMatch(it, it.categoryId, it.merchantName ?: merchant) }
}

/** The [rows] that [rule] alone would categorize, as (row id, match). A better rule must not hide a row from it. */
fun matchRows(
    rule: KeywordRule,
    rows: List<Transaction>,
    categories: Map<String, Category>,
): List<Pair<String, RuleMatch>> =
    rows.mapNotNull { row ->
        classify(row.merchantName, row.note, row.type, listOf(rule), categories)?.let { row.id to it }
    }

private fun Category?.isUsable(type: CategoryType): Boolean =
    this != null && deletedAt == null && !isHidden && this.type == type

/** Whether [text] (one field) matches this rule's keyword. Ignores [KeywordRule.isActive]. */
fun KeywordRule.matches(text: String): Boolean =
    when (matchType) {
        KeywordMatchType.CONTAINS -> {
            containsPhrase(text, keyword)
        }

        KeywordMatchType.EXACT -> {
            normalizedKeyword.isNotEmpty() && normalizeKeyword(text) == normalizedKeyword
        }

        // shortcut: a user pattern can backtrack badly; add a timeout if the UI ever offers REGEX.
        KeywordMatchType.REGEX -> {
            try {
                Regex(keyword, RegexOption.IGNORE_CASE).containsMatchIn(text)
            } catch (_: PatternSyntaxException) {
                false
            }
        }
    }

/**
 * A starting keyword for a new rule: the merchant; else the words before `chuyen tien` / `chuyen khoan` in the note;
 * else the note cut to 30 characters at a word boundary. `null` when both are blank.
 */
fun guessKeyword(
    merchant: String?,
    note: String?,
): String? = merchant?.trim()?.takeIf { it.isNotEmpty() } ?: note?.let(::guessFromNote)

private fun guessFromNote(note: String): String? {
    val words = note.trim().split(WHITESPACE).filter { it.isNotEmpty() }
    if (words.isEmpty()) return null
    val normalized = words.map(::normalizeForMatch)
    val transferAt =
        normalized.indices.firstOrNull { normalized[it] == "chuyen" && normalized.getOrNull(it + 1) in TRANSFER_WORDS }
    val before = words.take(transferAt ?: 0)
    return if (before.isNotEmpty()) before.joinToString(" ") else cutAtWord(words.joinToString(" "))
}

private fun cutAtWord(text: String): String {
    if (text.length <= MAX_GUESS) return text
    val head = text.take(MAX_GUESS + 1)
    return head.substring(0, head.lastIndexOf(' ').takeIf { it > 0 } ?: MAX_GUESS)
}
