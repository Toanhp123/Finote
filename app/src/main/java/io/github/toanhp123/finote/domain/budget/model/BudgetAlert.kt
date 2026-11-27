package io.github.toanhp123.finote.domain.budget.model

import java.time.YearMonth

/**
 * A budget that reached [mark] percent in [month]. [categoryId] is null for the total budget.
 * A system category carries [categoryNameKey] so the notifier can localize it.
 */
data class BudgetAlert(
    val month: YearMonth,
    val categoryId: String?,
    val categoryName: String?,
    val categoryNameKey: String?,
    val mark: Int,
) {
    // A category name is user data: it must never reach a log.
    override fun toString() = "BudgetAlert(month=$month, mark=$mark)"
}
