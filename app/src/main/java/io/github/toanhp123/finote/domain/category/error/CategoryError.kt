package io.github.toanhp123.finote.domain.category.error

/** Why a category change was refused. Decided in the domain, before anything is written. */
sealed interface CategoryError {
    /** Missing or soft-deleted. */
    data object NotFound : CategoryError

    data object BlankName : CategoryError

    data object TypeChanged : CategoryError

    data object SystemParentChanged : CategoryError

    /** Delete of a system category. */
    data object SystemCategory : CategoryError

    data object HasChildren : CategoryError

    /** The move target of a delete is missing, deleted, of the other type, or inside the deleted branch. */
    data object InvalidMoveTarget : CategoryError

    /** The parent is the category itself, missing, deleted, not top level, or of the other type. */
    data object InvalidParent : CategoryError
}
