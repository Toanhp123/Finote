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

    fun checkDelete(
        old: Category,
        liveChildren: Int,
    ): CategoryError? =
        when {
            old.isSystemDefault -> CategoryError.SystemCategory
            liveChildren > 0 -> CategoryError.HasChildren
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
