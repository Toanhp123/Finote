package io.github.toanhp123.finote.domain.budget.model

import java.time.YearMonth

/** The budgets of one [month] with their status. [categories] are sorted for display. */
data class BudgetMonth(
    val month: YearMonth,
    val total: BudgetStatus?,
    val categories: List<BudgetStatus>,
) {
    // Statuses hold money: counts only.
    override fun toString() = "BudgetMonth(month=$month, hasTotal=${total != null}, categories=${categories.size})"
}
