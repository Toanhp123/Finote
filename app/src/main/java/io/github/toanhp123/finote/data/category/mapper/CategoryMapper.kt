package io.github.toanhp123.finote.data.category.mapper

import io.github.toanhp123.finote.data.category.local.entity.CategoryEntity
import io.github.toanhp123.finote.domain.category.model.Category

fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        nameKey = nameKey,
        icon = icon,
        color = color,
        type = type,
        parentId = parentId,
        isSystemDefault = isSystemDefault,
        isHidden = isHidden,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

fun Category.toEntity(): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        nameKey = nameKey,
        icon = icon,
        color = color,
        type = type,
        parentId = parentId,
        isSystemDefault = isSystemDefault,
        isHidden = isHidden,
        sortOrder = sortOrder,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )
