package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.service.CategoryVisibility

/**
 * What the parent field offers: shown top-level categories of [type], not [editing] itself, plus the current parent of
 * [editing] and the parent the draft holds ([draftParentId], a preset from "+ Add subcategory") even when hidden, so
 * the field can still show them.
 */
fun parentOptions(
    all: List<Category>,
    editing: Category?,
    type: CategoryType,
    draftParentId: String? = null,
): List<Category> =
    all.filter {
        it.type == type &&
            it.parentId == null &&
            it.id != editing?.id &&
            (CategoryVisibility.isShown(it, null) || it.id == editing?.parentId || it.id == draftParentId)
    }
