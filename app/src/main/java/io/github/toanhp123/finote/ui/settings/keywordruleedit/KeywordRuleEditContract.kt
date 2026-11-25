package io.github.toanhp123.finote.ui.settings.keywordruleedit

import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.KeywordRuleDraft

/**
 * [draft] is the form: filled once from the first stored value (or blank in create mode, where [stored] stays null),
 * then owned by the screen. [stored] and [categories] follow the database; the screen closes when [stored] is gone.
 * [categories] are the picker choices. [error] is the last refused save. [bulkApplyCount] is set while the screen asks
 * whether the saved rule should also set the category of that many past rows.
 */
data class KeywordRuleEditState(
    val draft: KeywordRuleDraft? = null,
    val stored: KeywordRule? = null,
    val categories: List<Category> = emptyList(),
    val error: KeywordRuleError? = null,
    val bulkApplyCount: Int? = null,
    val isDeleteOpen: Boolean = false,
    val isSaving: Boolean = false,
) {
    val isSaveEnabled: Boolean
        get() = draft != null && draft.keyword.isNotBlank() && draft.categoryId != null && !isSaving
}

sealed interface KeywordRuleEditIntent {
    /** The whole form after one field changed. */
    data class DraftChanged(
        val draft: KeywordRuleDraft,
    ) : KeywordRuleEditIntent

    data object SaveClicked : KeywordRuleEditIntent

    data object DeleteClicked : KeywordRuleEditIntent

    data object DeleteConfirmed : KeywordRuleEditIntent

    data object DeleteDismissed : KeywordRuleEditIntent

    data object BulkApplyConfirmed : KeywordRuleEditIntent

    data object BulkApplyDismissed : KeywordRuleEditIntent
}

sealed interface KeywordRuleEditEffect {
    /** Leave the screen: the rule was saved (and the bulk question answered), deleted, or is gone. */
    data object Close : KeywordRuleEditEffect

    /** A write failed in the database; the route shows a snackbar. */
    data object ActionFailed : KeywordRuleEditEffect
}
