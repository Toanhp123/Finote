package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetAlert
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.budget.port.BudgetNotifier
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.service.BudgetMath
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

class CheckBudgetAlertsUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val categories: CategoryRepository,
        private val observeMonth: ObserveBudgetMonthUseCase,
        private val notifier: BudgetNotifier,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Alerts on the current month, read anew on every call. The marks are decided and written in one write
         * transaction that re-reads each row, and the notifications go out after the commit, so two checks never
         * both notify.
         */
        suspend operator fun invoke() {
            if (!budgets.hasAny()) return
            val month = observeMonth(YearMonth.now(clock)).first()
            val due =
                (listOfNotNull(month.total) + month.categories).mapNotNull { status ->
                    BudgetMath.alertToSend(status)?.let { status to it }
                }
            if (due.isEmpty()) return
            val sent =
                tx.write<List<BudgetAlert>, Nothing> {
                    Outcome.Ok(due.mapNotNull { (status, mark) -> claim(status, mark) })
                }
            if (sent is Outcome.Ok) sent.value.forEach(notifier::notify)
        }

        /** Writes the mark and returns the alert, or null when the row is gone or changed since [status]. */
        private suspend fun claim(
            status: BudgetStatus,
            mark: Int,
        ): BudgetAlert? {
            val row = budgets.get(status.budget.id)
            if (row == null || row.isChangedSince(status.budget)) return null
            budgets.update(row.copy(lastAlertSentThreshold = mark, updatedAt = clock.millis()))
            val category = row.categoryId?.let { categories.getCategory(it) }
            return BudgetAlert(row.month, row.categoryId, category?.name, category?.nameKey, mark)
        }

        private fun Budget.isChangedSince(old: Budget) =
            limitAmount != old.limitAmount ||
                alertThreshold != old.alertThreshold ||
                isRollover != old.isRollover ||
                categoryId != old.categoryId ||
                lastAlertSentThreshold != old.lastAlertSentThreshold
    }
