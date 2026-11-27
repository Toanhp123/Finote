package io.github.toanhp123.finote.domain.budget.model

enum class BudgetState { NORMAL, WARNING, OVER }

/**
 * A [budget] measured against [spent] (negative with refunds). [available] is the limit plus [carry].
 * [percent] is floored and null when [available] is not positive.
 */
data class BudgetStatus(
    val budget: Budget,
    val spent: Long,
    val carry: Long,
    val available: Long,
    val state: BudgetState,
    val percent: Int?,
) {
    val remaining get() = Math.subtractExact(available, spent)

    // Money must never reach a log or crash report.
    override fun toString() = "BudgetStatus(budget=$budget, state=$state)"
}
