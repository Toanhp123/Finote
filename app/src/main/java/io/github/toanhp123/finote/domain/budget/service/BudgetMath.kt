package io.github.toanhp123.finote.domain.budget.service

import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetState
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import java.time.YearMonth

private const val PERCENT = 100L

/** Budget status, rollover and alert marks. Pure math, exact: Long overflow throws [ArithmeticException]. */
object BudgetMath {
    fun spentOf(
        budget: Budget,
        report: MonthReport,
    ): Long {
        val id = budget.categoryId ?: return report.expense
        return report.buckets.firstOrNull { it.filter == ReportFilter.ByCategory(id) }?.amount ?: 0L
    }

    fun status(
        budget: Budget,
        spent: Long,
        carry: Long,
    ): BudgetStatus {
        val available = Math.addExact(budget.limitAmount, carry)
        val isOver = available <= 0 || spent >= available
        val state =
            when {
                isOver -> {
                    BudgetState.OVER
                }

                Math.multiplyExact(spent, PERCENT) >= Math.multiplyExact(available, budget.alertThreshold.toLong()) -> {
                    BudgetState.WARNING
                }

                else -> {
                    BudgetState.NORMAL
                }
            }
        val percent =
            if (available <= 0) {
                null
            } else {
                Math
                    .floorDiv(Math.multiplyExact(spent, PERCENT), available)
                    .coerceIn(0L, Int.MAX_VALUE.toLong())
                    .toInt()
            }
        return BudgetStatus(budget, spent, carry, available, state, percent)
    }

    /** Statuses of the [month] rows. A month missing from [reports] counts as spent 0. */
    fun statuses(
        month: YearMonth,
        rows: List<Budget>,
        reports: Map<YearMonth, MonthReport>,
    ): List<BudgetStatus> {
        val byKey = rows.associateBy { it.month to it.categoryId }
        return rows
            .filter { it.month == month }
            .map { status(it, spentIn(it, reports), carryOf(it, byKey, reports)) }
    }

    /** The mark this status has reached: 100 when used up with spending, else the threshold when warning. */
    fun reachedMark(status: BudgetStatus): Int? =
        when {
            status.state == BudgetState.OVER -> BudgetRules.FULL.takeIf { status.spent > 0 }
            status.state == BudgetState.WARNING -> status.budget.alertThreshold
            else -> null
        }

    /** The mark to notify now, or null. Never a mark at or below the one already sent. */
    fun alertToSend(status: BudgetStatus): Int? {
        val sent = status.budget.lastAlertSentThreshold
        return reachedMark(status)?.takeIf { sent == null || it > sent }
    }

    private fun spentIn(
        budget: Budget,
        reports: Map<YearMonth, MonthReport>,
    ): Long = reports[budget.month]?.let { spentOf(budget, it) } ?: 0L

    // Recursion depth is the length of the rollover chain: one row per month.
    private fun carryOf(
        budget: Budget,
        byKey: Map<Pair<YearMonth, String?>, Budget>,
        reports: Map<YearMonth, MonthReport>,
    ): Long {
        val previous = byKey[budget.month.minusMonths(1) to budget.categoryId]
        return if (!budget.isRollover || previous == null) {
            0L
        } else {
            val available = Math.addExact(previous.limitAmount, carryOf(previous, byKey, reports))
            Math.subtractExact(available, spentIn(previous, reports))
        }
    }
}
