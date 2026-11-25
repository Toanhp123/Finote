package io.github.toanhp123.finote.ui.settings.categories.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType

/** A top-level [category] with all its live [children], hidden ones included, in `sortOrder`. */
data class CategoryNode(
    val category: Category,
    val children: List<Category>,
)

/** [hidden] holds the hidden top-level nodes with all their children; a hidden child stays in its parent's node. */
data class CategoryTree(
    val shown: List<CategoryNode>,
    val hidden: List<CategoryNode>,
)

/** The tree of one [type] from the live categories in [all]. Both levels follow `sortOrder`. */
fun buildTree(
    all: List<Category>,
    type: CategoryType,
): CategoryTree {
    val live = all.filter { it.deletedAt == null }.sortedBy { it.sortOrder }
    val childrenOf = live.filter { it.parentId != null }.groupBy { it.parentId }
    val (hidden, shown) =
        live
            .filter { it.type == type && it.parentId == null }
            .map { CategoryNode(it, childrenOf[it.id].orEmpty()) }
            .partition { it.category.isHidden }
    return CategoryTree(shown, hidden)
}
