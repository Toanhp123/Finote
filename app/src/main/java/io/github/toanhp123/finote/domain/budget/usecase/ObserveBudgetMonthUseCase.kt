package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.model.BudgetMonth
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.service.BudgetMath
import io.github.toanhp123.finote.domain.budget.service.BudgetReports
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** The budgets of a month with spent, carry and state. Copies the month forward first when it is the current one. */
class ObserveBudgetMonthUseCase
    @Inject
    constructor(
        private val budgets: BudgetRepository,
        private val ensure: EnsureBudgetMonthUseCase,
        private val reportInput: ObserveReportInputUseCase,
        private val clock: Clock,
    ) {
        /** [month] defaults to the current month of the clock, read when called. */
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(month: YearMonth = YearMonth.now(clock)): Flow<BudgetMonth> =
            flow {
                if (month == YearMonth.now(clock)) ensure(month)
                emitAll(
                    budgets.observeFirstSetUp().flatMapLatest { first ->
                        // shortcut: loads every month since the first budget, revisit with N8 (step 30)
                        val from = minOf(first ?: month, month)
                        combine(budgets.observeUpTo(month), reportInput(from, month)) { rows, input ->
                            val reports = BudgetReports.between(input, from, month, LocalDate.now(clock))
                            toBudgetMonth(month, BudgetMath.statuses(month, rows, reports), input)
                        }
                    },
                )
            }

        /** Over first (no percent counts as over), then percent, spent, the category order of the report input. */
        private fun toBudgetMonth(
            month: YearMonth,
            statuses: List<BudgetStatus>,
            input: ReportInput,
        ): BudgetMonth {
            val order = input.categories.withIndex().associate { it.value.id to it.index }
            val (totals, categories) = statuses.partition { it.budget.isTotal }
            return BudgetMonth(
                month = month,
                total = totals.firstOrNull(),
                categories =
                    categories.sortedWith(
                        compareByDescending<BudgetStatus> { it.percent ?: Int.MAX_VALUE }
                            .thenByDescending { it.spent }
                            .thenBy { order[it.budget.categoryId] ?: Int.MAX_VALUE }
                            .thenBy { it.budget.categoryId.orEmpty() },
                    ),
            )
        }
    }
