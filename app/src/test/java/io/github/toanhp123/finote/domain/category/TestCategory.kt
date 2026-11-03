package io.github.toanhp123.finote.domain.category

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType

internal fun testCategory(
    id: String = "cat-1",
    name: String = "Food",
    type: CategoryType = CategoryType.EXPENSE,
    parentId: String? = null,
) = Category(
    id = id,
    name = name,
    nameKey = null,
    icon = "restaurant",
    color = 0,
    type = type,
    parentId = parentId,
    isSystemDefault = false,
    isHidden = false,
    sortOrder = 0,
    createdAt = 1L,
    updatedAt = 1L,
    deletedAt = null,
)
