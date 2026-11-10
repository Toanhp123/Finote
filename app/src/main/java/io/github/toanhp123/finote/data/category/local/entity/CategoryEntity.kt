package io.github.toanhp123.finote.data.category.local.entity

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.domain.category.model.CategoryType

@Entity(
    tableName = "categories",
    foreignKeys =
        [
            ForeignKey(
                entity = CategoryEntity::class,
                parentColumns = ["id"],
                childColumns = ["parentId"],
                onDelete = ForeignKey.RESTRICT,
            ),
        ],
    indices = [Index(value = ["parentId"])],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** String key of a system category. Null once the user renames it. */
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
