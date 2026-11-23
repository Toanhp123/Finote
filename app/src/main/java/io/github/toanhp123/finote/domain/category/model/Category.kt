package io.github.toanhp123.finote.domain.category.model

/**
 * A two-level category. [nameKey] is the string-resource key of a system category and is null once the user renames
 * it; the UI shows the resource when it is set and [name] otherwise. Every `Long` time field is epoch ms.
 */
data class Category(
    val id: String,
    val name: String,
    val nameKey: String?,
    val icon: String,
    val color: Int,
    val type: CategoryType,
    val parentId: String?,
    val isSystemDefault: Boolean,
    val isHidden: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
)
