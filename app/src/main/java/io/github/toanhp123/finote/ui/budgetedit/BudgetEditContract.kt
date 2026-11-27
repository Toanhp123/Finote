package io.github.toanhp123.finote.ui.budgetedit

import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.service.BudgetRules
import io.github.toanhp123.finote.domain.category.model.Category

/**
 * A failed write that the user can read about, shown above Save until the next edit or save. The sheet is its own
 * window, so a message outside it would be hidden. [FAILED] is a storage failure.
 */
enum class BudgetEditError { DUPLICATE, INVALID_CATEGORY, FAILED }

/**
 * [id] is the edited budget, null when creating. [categoryId] null is the total budget. [choices] are the categories
 * the user may pick (null is the total): in create mode only the free ones, in edit mode just the locked one.
 * [limitError] is the field error and [formError] the message above Save. [isVisible] turns false when the sheet
 * should leave; the route closes it once it has settled hidden.
 */
data class BudgetEditState(
    val id: String? = null,
    val categoryId: String? = null,
    val isCategoryLocked: Boolean = false,
    val choices: List<Category?> = emptyList(),
    val limitText: String = "",
    val isRollover: Boolean = false,
    val threshold: Int = BudgetRules.DEFAULT_THRESHOLD,
    val limitError: BudgetError? = null,
    val formError: BudgetEditError? = null,
    val isVisible: Boolean = true,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDeleteDialogShown: Boolean = false,
) {
    val isEditing get() = id != null

    // The limit is money: never in a log.
    override fun toString() = "BudgetEditState(id=$id, isLoading=$isLoading, isSaving=$isSaving, isVisible=$isVisible)"
}

sealed interface BudgetEditIntent {
    data class CategoryPicked(
        val categoryId: String?,
    ) : BudgetEditIntent

    /** The raw text of the field; the view model keeps the digits. */
    data class LimitChanged(
        val text: String,
    ) : BudgetEditIntent

    data class RolloverChanged(
        val isRollover: Boolean,
    ) : BudgetEditIntent

    data class ThresholdPicked(
        val threshold: Int,
    ) : BudgetEditIntent

    data object SaveClicked : BudgetEditIntent

    data object DeleteClicked : BudgetEditIntent

    data object DeleteConfirmed : BudgetEditIntent

    data object DeleteDismissed : BudgetEditIntent

    /** A swipe down, a tap outside or Back. */
    data object Dismissed : BudgetEditIntent

    /** The user answered the notification permission dialog, whatever the answer. */
    data object PermissionAnswered : BudgetEditIntent
}

sealed interface BudgetEditEffect {
    /** The first budget was saved: ask for the notification permission, then send `PermissionAnswered`. */
    data object AskNotificationPermission : BudgetEditEffect
}
