package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.model.Category

/** A category is shown when it and its parent are live and not hidden. `parent` is null for a top-level category. */
object CategoryVisibility {
    fun isShown(
        category: Category,
        parent: Category?,
    ): Boolean =
        category.deletedAt == null &&
            !category.isHidden &&
            (parent == null || (!parent.isHidden && parent.deletedAt == null))
}

/** Parents are looked up in this list; a child whose parent is not in it counts as having no parent. */
fun List<Category>.shown(): List<Category> {
    val byId = associateBy { it.id }
    return filter { CategoryVisibility.isShown(it, it.parentId?.let(byId::get)) }
}
