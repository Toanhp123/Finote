package io.github.toanhp123.finote.domain.category.model

enum class KeywordMatchType { CONTAINS, EXACT, REGEX }

/**
 * Maps a notification keyword to a category and an optional short merchant name. System rules are `CONTAINS` only.
 * [keyword] is as typed, [normalizedKeyword] is `normalizeKeyword(keyword)`. Every `Long` time field is epoch ms.
 */
data class KeywordRule(
    val id: String,
    val keyword: String,
    val normalizedKeyword: String,
    val matchType: KeywordMatchType,
    val merchantName: String?,
    val categoryId: String,
    val priority: Int,
    val isUserDefined: Boolean,
    val isActive: Boolean,
    val version: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    // Keyword and name come from the user's notifications; they must not reach a log.
    override fun toString(): String = "KeywordRule(id=$id)"
}

/** The rule that matched, with the category it sets and the merchant name the row keeps. */
data class RuleMatch(
    val rule: KeywordRule,
    val categoryId: String,
    val merchantName: String?,
) {
    // merchantName comes from the user's notifications; it must not reach a log.
    override fun toString(): String = "RuleMatch(ruleId=${rule.id}, categoryId=$categoryId)"
}
