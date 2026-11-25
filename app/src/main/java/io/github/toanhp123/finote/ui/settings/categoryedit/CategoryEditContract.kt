package io.github.toanhp123.finote.ui.settings.categoryedit

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.CategoryDraft
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteChoice
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.DeleteSheet
import io.github.toanhp123.finote.ui.settings.categoryedit.logic.parentOptions

/**
 * [draft] is the form: filled once from the first stored value (or the create defaults, where [stored] is null and
 * stays null), then owned by the screen. [stored] and [categories] follow the database. [stored] turns null when the
 * category is gone, and the screen closes. [type] is the stored type, or the type of the category being created.
 * [delete] is the open delete sheet, if any.
 */
data class CategoryEditState(
    val type: CategoryType,
    val draft: CategoryDraft? = null,
    val stored: Category? = null,
    /** Every live category, hidden ones included. */
    val categories: List<Category> = emptyList(),
    val delete: DeleteSheet? = null,
    val isSaving: Boolean = false,
) {
    /** A system category keeps its parent, and so does a parent with live children (a child cannot have children). */
    val isParentLocked: Boolean
        get() = stored != null && (stored.isSystemDefault || categories.any { it.parentId == stored.id })

    /** The stored category shows nowhere because its parent is hidden: the screen says why. */
    val isHiddenByParent: Boolean
        get() = stored?.parentId?.let { id -> categories.firstOrNull { it.id == id }?.isHidden } == true

    /** Live children, hidden ones included: only for a stored top-level category whose draft parent is unchanged. */
    val children: List<Category>
        get() =
            if (stored != null && stored.parentId == null && draft?.parentId == null) {
                categories.filter { it.parentId == stored.id }
            } else {
                emptyList()
            }

    /** What the parent field offers. */
    val parents: List<Category>
        get() = parentOptions(categories, stored, type, draft?.parentId)

    val isSaveEnabled: Boolean
        get() = draft != null && draft.name.isNotBlank() && !isSaving
}

sealed interface CategoryEditIntent {
    /** The whole form after one field changed. The view model keeps the parent while it is locked. */
    data class DraftChanged(
        val draft: CategoryDraft,
    ) : CategoryEditIntent

    data object SaveClicked : CategoryEditIntent

    /** Hides a shown category, shows a hidden one. */
    data object HiddenToggled : CategoryEditIntent

    /** Opens create for a new child of this category. */
    data object AddSubcategoryClicked : CategoryEditIntent

    /** The children in their new order. */
    data class ChildrenReordered(
        val ids: List<String>,
    ) : CategoryEditIntent

    data object DeleteClicked : CategoryEditIntent

    data class DeleteChoicePicked(
        val choice: DeleteChoice,
    ) : CategoryEditIntent

    data object DeleteConfirmed : CategoryEditIntent

    data object DeleteDismissed : CategoryEditIntent
}

sealed interface CategoryEditEffect {
    /** Leave the screen: the form was written, the category was deleted, or it is gone. */
    data object Close : CategoryEditEffect

    data class OpenEditCategory(
        val key: EditCategory,
    ) : CategoryEditEffect

    /** A write did not happen. [error] is the rule that refused it; null is a database failure. */
    data class ActionFailed(
        val error: CategoryError?,
    ) : CategoryEditEffect
}
