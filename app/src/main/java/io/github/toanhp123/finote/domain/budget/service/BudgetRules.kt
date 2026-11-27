package io.github.toanhp123.finote.domain.budget.service

object BudgetRules {
    /** 13 digits, the same cap as an amount expression. */
    const val MAX_LIMIT = 9_999_999_999_999L
    val THRESHOLDS = listOf(50, 70, 80, 90)
    const val DEFAULT_THRESHOLD = 80

    /** The mark of a budget that is used up. */
    const val FULL = 100
}
