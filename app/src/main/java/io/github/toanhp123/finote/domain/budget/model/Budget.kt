package io.github.toanhp123.finote.domain.budget.model

import java.time.YearMonth

/**
 * A spending plan for one [month]. [categoryId] is a top-level category, or null for the total budget. Money is VND.
 * [lastAlertSentThreshold] is the highest mark already notified (see `BudgetMath.alertToSend`).
 */
data class Budget(
    val id: String,
    val categoryId: String?,
    val limitAmount: Long,
    val month: YearMonth,
    val isRollover: Boolean,
    val alertThreshold: Int,
    val lastAlertSentThreshold: Int?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val isTotal get() = categoryId == null

    // The limit must never reach a log or crash report.
    override fun toString() = "Budget(id=$id, month=$month)"
}
