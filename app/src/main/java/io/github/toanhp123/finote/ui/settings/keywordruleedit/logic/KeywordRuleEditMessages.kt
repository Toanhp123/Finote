package io.github.toanhp123.finote.ui.settings.keywordruleedit.logic

import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.error.KeywordRuleError

/** The message for a refused save, shown under the keyword field. */
@StringRes
fun KeywordRuleError.messageRes(): Int =
    when (this) {
        KeywordRuleError.TooShort -> R.string.cash_entry_rule_error_too_short
        KeywordRuleError.Duplicate -> R.string.cash_entry_rule_error_duplicate
        KeywordRuleError.CategoryGone -> R.string.cash_entry_rule_error_category_gone
        KeywordRuleError.NotFound -> R.string.cash_entry_rule_error_not_found
    }
