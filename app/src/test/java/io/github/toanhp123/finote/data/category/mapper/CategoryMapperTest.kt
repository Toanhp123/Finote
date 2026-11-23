package io.github.toanhp123.finote.data.category.mapper

import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryMapperTest {
    @Test
    fun `round-trips every field`() {
        val entity =
            category(id = "cat-9", parentId = "cat-1", name = "Coffee", sortOrder = 3, deletedAt = 5L)
                .copy(isHidden = true)
                .copy(
                    nameKey = "sys_food_coffee",
                    icon = "coffee",
                    color = 0xFF1565C0.toInt(),
                    type = CategoryType.INCOME,
                    isSystemDefault = true,
                    createdAt = 11L,
                    updatedAt = 22L,
                )
        assertEquals(entity, entity.toDomain().toEntity())
    }
}
