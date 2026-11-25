package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category

/**
 * Pure two-level tree rules. `null` means allowed. `parent` is the stored row found by `category.parentId`:
 * null when `parentId` is null or the row is missing.
 */
object CategoryRules {
    fun checkNew(
        category: Category,
        parent: Category?,
    ): CategoryError? =
        when {
            category.name.isBlank() -> CategoryError.BlankName
            !parentIsValid(category, parent) -> CategoryError.InvalidParent
            else -> null
        }

    fun checkUpdate(
        old: Category,
        new: Category,
        parent: Category?,
        liveChildren: Int,
    ): CategoryError? =
        when {
            new.type != old.type -> CategoryError.TypeChanged
            old.isSystemDefault && new.parentId != old.parentId -> CategoryError.SystemParentChanged
            !parentIsValid(new, parent) -> CategoryError.InvalidParent
            new.parentId != null && liveChildren > 0 -> CategoryError.HasChildren
            new.name.isBlank() -> CategoryError.BlankName
            else -> null
        }

    fun checkDelete(old: Category): CategoryError? = if (old.isSystemDefault) CategoryError.SystemCategory else null

    /**
     * `target` is the stored row of a `MoveTarget.Into`, null when missing. It must be live, of the deleted
     * category's type, and outside the deleted branch (`branchIds`: the category and its children).
     */
    fun checkMoveTarget(
        deleted: Category,
        target: Category?,
        branchIds: Set<String>,
    ): CategoryError? =
        when {
            target == null || target.deletedAt != null -> CategoryError.InvalidMoveTarget
            target.type != deleted.type || target.id in branchIds -> CategoryError.InvalidMoveTarget
            else -> null
        }

    /** No parent is valid. A parent must be live, top level, of the same type, and not the category itself. */
    private fun parentIsValid(
        category: Category,
        parent: Category?,
    ): Boolean =
        category.parentId == null ||
            (
                parent != null &&
                    parent.id != category.id &&
                    parent.deletedAt == null &&
                    parent.parentId == null &&
                    parent.type == category.type
            )
}
