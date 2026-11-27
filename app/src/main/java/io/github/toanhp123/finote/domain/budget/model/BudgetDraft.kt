package io.github.toanhp123.finote.domain.budget.model

/** What the edit sheet submits. [id] is null for a new budget; [categoryId] null is the total budget. */
data class BudgetDraft(
    val id: String?,
    val categoryId: String?,
    val limitAmount: Long,
    val isRollover: Boolean,
    val alertThreshold: Int,
) {
    // The limit must never reach a log or crash report.
    override fun toString() = "BudgetDraft(id=$id, categoryId=$categoryId)"
}
