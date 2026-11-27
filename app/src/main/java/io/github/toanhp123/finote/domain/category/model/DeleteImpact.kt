package io.github.toanhp123.finote.domain.category.model

/** What deleting a category and its live children moves: live transactions, the user's keyword rules and budgets. */
data class DeleteImpact(
    val transactions: Int,
    val rules: Int,
    val budgets: Int,
) {
    val isEmpty: Boolean get() = transactions == 0 && rules == 0 && budgets == 0
}
