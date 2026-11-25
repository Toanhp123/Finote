package io.github.toanhp123.finote.ui.settings.categories

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.ui.navigation.EditCategory
import io.github.toanhp123.finote.ui.settings.categories.logic.CategoryTree

/**
 * [type] is the selected tab and [tree] its categories. [expanded] holds the ids of the open top-level rows.
 * [reorder] is the draft order of the visible top-level rows while the reorder mode is on, and null otherwise.
 */
data class CategoriesSettingsState(
    val type: CategoryType = CategoryType.EXPENSE,
    val tree: CategoryTree = CategoryTree(emptyList(), emptyList()),
    val expanded: Set<String> = emptySet(),
    val isHiddenExpanded: Boolean = false,
    val reorder: List<Category>? = null,
)

sealed interface CategoriesSettingsIntent {
    /** The user picked a tab; this ends the reorder mode. */
    data class TabSelected(
        val type: CategoryType,
    ) : CategoriesSettingsIntent

    data class ExpandToggled(
        val id: String,
    ) : CategoriesSettingsIntent

    data object HiddenSectionToggled : CategoriesSettingsIntent

    /** The eye button of [id]: hide it when shown, show it when hidden. */
    data class VisibilityToggled(
        val id: String,
    ) : CategoriesSettingsIntent

    data class CategoryClicked(
        val id: String,
    ) : CategoriesSettingsIntent

    data object AddClicked : CategoriesSettingsIntent

    data object ReorderClicked : CategoriesSettingsIntent

    /** The draft row at index [from] was dropped at index [to]. */
    data class ReorderMoved(
        val from: Int,
        val to: Int,
    ) : CategoriesSettingsIntent

    data object ReorderDone : CategoriesSettingsIntent

    data object ReorderCancelled : CategoriesSettingsIntent
}

sealed interface CategoriesSettingsEffect {
    data class OpenEditCategory(
        val key: EditCategory,
    ) : CategoriesSettingsEffect

    /** A write failed; the route shows a snackbar. */
    data object ActionFailed : CategoriesSettingsEffect
}
