package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType

/**
 * The fields the edit screen owns until Save. [name] starts as the stored `name`. The screen shows
 * `displayName(stored.name, stored.nameKey)` while the draft name still equals the stored one, so a system category
 * reads in the user's language until the user types.
 */
data class CategoryDraft(
    val name: String,
    val parentId: String?,
    val icon: String,
    val color: Int,
)

fun Category.toDraft() = CategoryDraft(name = name, parentId = parentId, icon = icon, color = color)

/**
 * The category to write. Without a [stored] row it is a new user category that the create use case stamps. With one,
 * an untouched name is the stored `name` again, so the repository keeps `nameKey`; an edited one clears it.
 */
fun CategoryDraft.toCategory(
    stored: Category?,
    type: CategoryType,
): Category {
    val sentName = name.trim()
    return stored?.copy(name = sentName, parentId = parentId, icon = icon, color = color)
        ?: Category(
            id = "",
            name = sentName,
            nameKey = null,
            icon = icon,
            color = color,
            type = type,
            parentId = parentId,
            isSystemDefault = false,
            isHidden = false,
            sortOrder = 0,
            createdAt = 0L,
            updatedAt = 0L,
            deletedAt = null,
        )
}
