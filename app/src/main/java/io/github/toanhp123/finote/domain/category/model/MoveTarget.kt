package io.github.toanhp123.finote.domain.category.model

/** Where the transactions of a deleted category go. */
sealed interface MoveTarget {
    data object Uncategorized : MoveTarget

    data class Into(
        val id: String,
    ) : MoveTarget
}
