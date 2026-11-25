package io.github.toanhp123.finote.ui.settings.keywordrules.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.KeywordRule

/** A rule with the category it points at. [category] is null when that category is gone. */
data class KeywordRuleItem(
    val rule: KeywordRule,
    val category: Category?,
)
