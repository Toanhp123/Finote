package io.github.toanhp123.finote.ui.settings.categories.logic

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryTreeTest {
    private fun cat(
        id: String,
        parentId: String? = null,
        type: CategoryType = CategoryType.EXPENSE,
        sortOrder: Int = 0,
        isHidden: Boolean = false,
    ): Category =
        testCategory(id = id, type = type, parentId = parentId).copy(sortOrder = sortOrder, isHidden = isHidden)

    @Test
    fun `hidden parent goes to the hidden section with its children`() {
        val tree =
            buildTree(
                listOf(
                    cat("food"),
                    cat("home", sortOrder = 1, isHidden = true),
                    cat("rent", parentId = "home"),
                    cat("bills", parentId = "home", sortOrder = 1),
                ),
                CategoryType.EXPENSE,
            )

        assertEquals(listOf("food"), tree.shown.map { it.category.id })
        assertEquals(listOf("home"), tree.hidden.map { it.category.id })
        assertEquals(
            listOf("rent", "bills"),
            tree.hidden
                .single()
                .children
                .map { it.id },
        )
    }

    @Test
    fun `hidden child stays under its visible parent`() {
        val tree =
            buildTree(
                listOf(
                    cat("food"),
                    cat("coffee", parentId = "food", sortOrder = 1, isHidden = true),
                    cat("lunch", parentId = "food"),
                ),
                CategoryType.EXPENSE,
            )

        assertEquals(listOf("food"), tree.shown.map { it.category.id })
        assertEquals(emptyList<CategoryNode>(), tree.hidden)
        assertEquals(
            listOf("lunch", "coffee"),
            tree.shown
                .single()
                .children
                .map { it.id },
        )
    }

    @Test
    fun `a tab shows only its type`() {
        val all =
            listOf(
                cat("food"),
                cat("salary", type = CategoryType.INCOME),
                cat("bonus", parentId = "salary", type = CategoryType.INCOME),
            )

        assertEquals(listOf("food"), buildTree(all, CategoryType.EXPENSE).shown.map { it.category.id })
        val income = buildTree(all, CategoryType.INCOME).shown
        assertEquals(listOf("salary"), income.map { it.category.id })
        assertEquals(listOf("bonus"), income.single().children.map { it.id })
    }

    @Test
    fun `top level rows follow sortOrder`() {
        val tree = buildTree(listOf(cat("b", sortOrder = 1), cat("a", sortOrder = 0)), CategoryType.EXPENSE)

        assertEquals(listOf("a", "b"), tree.shown.map { it.category.id })
    }
}
