package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.DeleteImpact
import io.github.toanhp123.finote.domain.category.model.MoveTarget

/** Where the transactions of a deleted category go, as the user picks it. [Unset] is "nothing picked yet". */
sealed interface DeleteChoice {
    data object Unset : DeleteChoice

    data object Uncategorized : DeleteChoice

    data class Into(
        val id: String,
    ) : DeleteChoice
}

/**
 * The open delete sheet. [impact] is what moves with the branch. [targets] are the categories the user may move it
 * into; "No category" is always offered besides. With nothing to move [choice] starts at
 * [DeleteChoice.Uncategorized], so a plain confirm works; otherwise it starts [DeleteChoice.Unset] and confirm waits.
 */
data class DeleteSheet(
    val impact: DeleteImpact,
    val targets: List<Category>,
    val choice: DeleteChoice,
)

/** The target to send to the delete use case, or null while nothing is picked. */
fun DeleteChoice.toMoveTarget(): MoveTarget? =
    when (this) {
        DeleteChoice.Unset -> null
        DeleteChoice.Uncategorized -> MoveTarget.Uncategorized
        is DeleteChoice.Into -> MoveTarget.Into(id)
    }
