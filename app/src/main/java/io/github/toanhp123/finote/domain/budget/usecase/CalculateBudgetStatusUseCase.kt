package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.service.BudgetMath
import io.github.toanhp123.finote.domain.budget.service.BudgetReports
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

class CalculateBudgetStatusUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val ensure: EnsureBudgetMonthUseCase,
        private val reportInput: ObserveReportInputUseCase,
        private val clock: Clock,
    ) {
        /**
         * The status [budget] would have in its month once stored: its row replaces the stored one with the same id,
         * or joins the month. One read, no flow kept open. The caller checks the limit first (overflow throws).
         */
        suspend operator fun invoke(budget: Budget): BudgetStatus {
            val month = budget.month
            if (month == YearMonth.now(clock)) ensure(month)
            // shortcut: loads every month since the first budget, revisit with N8 (step 30)
            val from = minOf(budgets.observeFirstSetUp().first() ?: month, month)
            val rows = budgets.observeUpTo(month).first().filter { it.id != budget.id } + budget
            val reports = BudgetReports.between(reportInput(from, month).first(), from, month, LocalDate.now(clock))
            return BudgetMath.statuses(month, rows, reports).first { it.budget.id == budget.id }
        }
    }
