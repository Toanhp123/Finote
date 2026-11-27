package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetDraft
import io.github.toanhp123.finote.domain.budget.model.SaveBudgetResult
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.repository.NotificationPromptRepository
import io.github.toanhp123.finote.domain.budget.service.BudgetMath
import io.github.toanhp123.finote.domain.budget.service.BudgetRules
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

@Suppress("LongParameterList") // why: one collaborator per store it reads or writes; the checks are not worth a class
class SaveBudgetUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val categories: CategoryRepository,
        private val calculateStatus: CalculateBudgetStatusUseCase,
        private val ensure: EnsureBudgetMonthUseCase,
        private val prompt: NotificationPromptRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Creates or edits a budget of the current month. The stored alert mark is the highest one the new values
         * already reach, so saving never fires an alert. The spending is read before the write, never inside it.
         */
        suspend operator fun invoke(draft: BudgetDraft): Outcome<SaveBudgetResult, BudgetError> {
            valueError(draft)?.let { return Outcome.Err(it) }
            val month = YearMonth.now(clock)
            val now = clock.millis()
            val candidate =
                Budget(
                    id = draft.id ?: UUID.randomUUID().toString(),
                    categoryId = draft.categoryId,
                    limitAmount = draft.limitAmount,
                    month = month,
                    isRollover = draft.isRollover,
                    alertThreshold = draft.alertThreshold,
                    lastAlertSentThreshold = null,
                    createdAt = now,
                    updatedAt = now,
                )
            val mark = BudgetMath.reachedMark(calculateStatus(candidate))
            val saved =
                tx.write {
                    // The copy must precede this save, or the month would be marked set up without it.
                    ensure(month)
                    val existing = draft.id?.let { budgets.get(it) }
                    val error = rowError(draft, existing, month)
                    if (error != null) {
                        Outcome.Err(error)
                    } else {
                        val row = candidate.copy(lastAlertSentThreshold = mark, createdAt = existing?.createdAt ?: now)
                        if (existing == null) budgets.insert(row) else budgets.update(row)
                        budgets.markSetUp(month)
                        Outcome.Ok(Unit)
                    }
                }
            return when (saved) {
                is Outcome.Err -> saved
                is Outcome.Ok -> Outcome.Ok(SaveBudgetResult(shouldAskNotificationPermission = askOnce()))
            }
        }

        private fun valueError(draft: BudgetDraft): BudgetError? =
            when {
                draft.limitAmount !in 1L..BudgetRules.MAX_LIMIT -> BudgetError.InvalidLimit
                draft.alertThreshold !in BudgetRules.THRESHOLDS -> BudgetError.InvalidThreshold
                else -> null
            }

        private suspend fun rowError(
            draft: BudgetDraft,
            existing: Budget?,
            month: YearMonth,
        ): BudgetError? =
            when {
                draft.id != null && existing == null -> {
                    BudgetError.NotFound
                }

                existing != null && existing.month != month -> {
                    BudgetError.NotCurrentMonth
                }

                // The category is locked on edit.
                existing != null && existing.categoryId != draft.categoryId -> {
                    BudgetError.InvalidCategory
                }

                !isLiveTopLevelExpense(draft.categoryId) -> {
                    BudgetError.InvalidCategory
                }

                // SQLite's unique index lets two total rows through, so the total is checked here too.
                budgets.getMonth(month).any { it.categoryId == draft.categoryId && it.id != draft.id } -> {
                    BudgetError.Duplicate
                }

                else -> {
                    null
                }
            }

        /** Null is the total budget. */
        private suspend fun isLiveTopLevelExpense(categoryId: String?): Boolean =
            categoryId == null ||
                categories.getCategory(categoryId)?.let {
                    it.deletedAt == null && it.parentId == null && it.type == CategoryType.EXPENSE
                } == true

        private fun askOnce(): Boolean {
            val isFirst = !prompt.wasAsked()
            if (isFirst) prompt.markAsked()
            return isFirst
        }
    }
