package io.github.toanhp123.finote.ui.budgetedit

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetDraft
import io.github.toanhp123.finote.domain.budget.model.BudgetMonth
import io.github.toanhp123.finote.domain.budget.usecase.DeleteBudgetUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.SaveBudgetUseCase
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.service.shown
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.cashentry.logic.AmountExpression
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Create mode ([id] null) edits a draft only. Edit mode fills the form once from the stored budget; later emissions
 * of the current month only refresh [BudgetEditState.choices] and close the sheet when the row is gone.
 */
@HiltViewModel(assistedFactory = BudgetEditViewModel.Factory::class)
class BudgetEditViewModel
    @AssistedInject
    constructor(
        @Assisted private val id: String?,
        observeBudgetMonth: ObserveBudgetMonthUseCase,
        observeCategories: ObserveCategoriesUseCase,
        private val saveBudget: SaveBudgetUseCase,
        private val deleteBudget: DeleteBudgetUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<BudgetEditState, BudgetEditIntent, BudgetEditEffect>(
            BudgetEditState(id = id),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(id: String?): BudgetEditViewModel
        }

        init {
            viewModelScope.launch {
                combine(observeBudgetMonth(), observeCategories()) { month, list -> month to list }
                    .catchStorage {
                        // Nothing was ever shown to edit, so there is nothing to keep.
                        if (state.value.isLoading) setState { copy(isLoading = false, isVisible = false) }
                    }.collect { (month, list) -> onData(month, list) }
            }
        }

        override fun onIntent(intent: BudgetEditIntent) {
            when (intent) {
                is BudgetEditIntent.CategoryPicked -> {
                    setState { if (isCategoryLocked) this else copy(categoryId = intent.categoryId, formError = null) }
                }

                is BudgetEditIntent.LimitChanged -> {
                    val digits = intent.text.filter(Char::isDigit).take(AmountExpression.MAX_DIGITS)
                    setState { copy(limitText = digits, limitError = null, formError = null) }
                }

                is BudgetEditIntent.RolloverChanged -> {
                    setState { copy(isRollover = intent.isRollover, formError = null) }
                }

                is BudgetEditIntent.ThresholdPicked -> {
                    setState { copy(threshold = intent.threshold, formError = null) }
                }

                BudgetEditIntent.SaveClicked -> {
                    save()
                }

                BudgetEditIntent.DeleteClicked -> {
                    if (state.value.let { it.isEditing && !it.isSaving }) setState { copy(isDeleteDialogShown = true) }
                }

                BudgetEditIntent.DeleteConfirmed -> {
                    delete()
                }

                BudgetEditIntent.DeleteDismissed -> {
                    setState { copy(isDeleteDialogShown = false) }
                }

                BudgetEditIntent.Dismissed, BudgetEditIntent.PermissionAnswered -> {
                    setState { copy(isVisible = false) }
                }
            }
        }

        private fun onData(
            month: BudgetMonth,
            categories: List<Category>,
        ) {
            val budgets = (listOfNotNull(month.total) + month.categories).map { it.budget }
            val stored = id?.let { id -> budgets.firstOrNull { it.id == id } }
            if (id != null && stored == null) {
                // Moved, deleted with its category, or never there: nothing left to edit.
                setState { copy(isLoading = false, isVisible = false, isDeleteDialogShown = false) }
                return
            }
            val choices = choicesOf(stored, budgets, categories)
            if (state.value.isLoading) {
                setState { filled(stored, choices) }
            } else {
                setState { copy(choices = choices) }
            }
        }

        /** The form is filled once, from the stored row or the create defaults. */
        private fun BudgetEditState.filled(
            stored: Budget?,
            choices: List<Category?>,
        ): BudgetEditState =
            if (stored != null) {
                copy(
                    isLoading = false,
                    isCategoryLocked = true,
                    choices = choices,
                    categoryId = stored.categoryId,
                    limitText = stored.limitAmount.toString(),
                    isRollover = stored.isRollover,
                    threshold = stored.alertThreshold,
                )
            } else {
                copy(isLoading = false, choices = choices, categoryId = choices.firstOrNull()?.id)
            }

        /**
         * Edit: the locked category alone (it may be hidden by now), or the total. A category budget whose category is
         * not in the list shows no chip: a "Total" chip would be wrong. Create: the total and the free shown ones.
         */
        private fun choicesOf(
            stored: Budget?,
            budgets: List<Budget>,
            categories: List<Category>,
        ): List<Category?> {
            if (stored != null) {
                val own = categories.firstOrNull { it.id == stored.categoryId }
                return if (stored.isTotal) listOf(null) else listOfNotNull(own)
            }
            val taken = budgets.map { it.categoryId }.toSet()
            val free =
                categories
                    .shown()
                    .filter { it.parentId == null && it.type == CategoryType.EXPENSE && it.id !in taken }
            return (if (null in taken) emptyList() else listOf(null)) + free
        }

        private fun save() {
            val current = state.value
            // The flag is set before the launch, so a double tap cannot reach the use case twice.
            if (current.isLoading || current.isSaving) return
            setState { copy(isSaving = true, formError = null) }
            val draft =
                BudgetDraft(
                    id = current.id,
                    categoryId = current.categoryId,
                    limitAmount = current.limitText.toLongOrNull() ?: 0L,
                    isRollover = current.isRollover,
                    alertThreshold = current.threshold,
                )
            launchCatching(onStorageError = ::failed) {
                when (val outcome = saveBudget(draft)) {
                    is Outcome.Ok -> {
                        // isSaving stays true: the sheet is on its way out, and a second save would be a duplicate.
                        if (outcome.value.shouldAskNotificationPermission) {
                            sendEffect(BudgetEditEffect.AskNotificationPermission)
                        } else {
                            setState { copy(isVisible = false) }
                        }
                    }

                    is Outcome.Err -> {
                        setState { copy(isSaving = false) }
                        showError(outcome.error)
                    }
                }
            }
        }

        private fun delete() {
            val current = state.value
            val budgetId = current.id
            if (budgetId == null || current.isSaving) return
            setState { copy(isDeleteDialogShown = false, isSaving = true, formError = null) }
            launchCatching(onStorageError = ::failed) {
                when (val outcome = deleteBudget(budgetId)) {
                    is Outcome.Ok -> {
                        setState { copy(isVisible = false) }
                    }

                    is Outcome.Err -> {
                        setState { copy(isSaving = false) }
                        showError(outcome.error)
                    }
                }
            }
        }

        // The limit has its own field error. A row that is gone or from another month leaves nothing to fix: the sheet
        // closes and the list under it shows the truth. Every other refusal is a message above Save: the sheet is its
        // own window, so a snackbar of the screen would be hidden under it.
        private fun showError(error: BudgetError) =
            setState {
                when (error) {
                    BudgetError.InvalidLimit -> copy(limitError = error)

                    BudgetError.Duplicate -> copy(formError = BudgetEditError.DUPLICATE)

                    BudgetError.InvalidCategory -> copy(formError = BudgetEditError.INVALID_CATEGORY)

                    BudgetError.NotCurrentMonth, BudgetError.NotFound -> copy(isVisible = false)

                    // The chips only offer valid marks, so this is a bug, not something the user can fix.
                    BudgetError.InvalidThreshold -> copy(formError = BudgetEditError.FAILED)
                }
            }

        private fun failed() = setState { copy(isSaving = false, formError = BudgetEditError.FAILED) }
    }
