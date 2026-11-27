package io.github.toanhp123.finote.domain.budget.error

/** Why a budget change was refused. Decided in the domain, before anything is written. */
sealed interface BudgetError {
    /** Edit or delete of a row that is not in the current month. */
    data object NotCurrentMonth : BudgetError

    /** Not in `1..BudgetRules.MAX_LIMIT`. */
    data object InvalidLimit : BudgetError

    /** Not one of `BudgetRules.THRESHOLDS`. */
    data object InvalidThreshold : BudgetError

    /** Not a live top-level Expense category. */
    data object InvalidCategory : BudgetError

    /** Another row of the month has the same category (or is also the total). */
    data object Duplicate : BudgetError

    data object NotFound : BudgetError
}
