package io.github.toanhp123.finote.ui.settings.keywordruleedit.logic

import io.github.toanhp123.finote.domain.category.model.KeywordRule

/** The form of a rule as typed. [categoryId] is null until the user picks one. */
data class KeywordRuleDraft(
    val keyword: String = "",
    val merchantName: String = "",
    val categoryId: String? = null,
) {
    // Keyword and name come from the user's notifications; they must not reach a log.
    override fun toString(): String = "KeywordRuleDraft(categoryId=$categoryId)"
}

fun KeywordRule.toDraft() = KeywordRuleDraft(keyword, merchantName.orEmpty(), categoryId)
