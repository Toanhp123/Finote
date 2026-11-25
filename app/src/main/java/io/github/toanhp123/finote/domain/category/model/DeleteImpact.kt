package io.github.toanhp123.finote.domain.category.model

/** What deleting a category and its live children moves: live transactions and the user's keyword rules. */
data class DeleteImpact(
    val transactions: Int,
    val rules: Int,
) {
    val isEmpty: Boolean get() = transactions == 0 && rules == 0
}
