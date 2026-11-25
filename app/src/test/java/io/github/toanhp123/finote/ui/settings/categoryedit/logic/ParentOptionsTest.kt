package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class ParentOptionsTest {
    private val expense = CategoryType.EXPENSE

    @Test
    fun `hidden parents are left out except the current one`() {
        val shown = testCategory(id = "a")
        val hidden = testCategory(id = "b").copy(isHidden = true)
        val current = testCategory(id = "c").copy(isHidden = true)
        val editing = testCategory(id = "e", parentId = "c")

        val options = parentOptions(listOf(shown, hidden, current, editing), editing, expense)

        assertEquals(listOf("a", "c"), options.map { it.id })
    }

    @Test
    fun `a hidden parent held by the draft is offered`() {
        val hidden = testCategory(id = "h").copy(isHidden = true)
        val other = testCategory(id = "o").copy(isHidden = true)

        val options = parentOptions(listOf(hidden, other), editing = null, type = expense, draftParentId = "h")

        assertEquals(listOf("h"), options.map { it.id })
    }

    @Test
    fun `the category itself is not offered`() {
        val editing = testCategory(id = "e")

        val options = parentOptions(listOf(editing, testCategory(id = "a")), editing, expense)

        assertEquals(listOf("a"), options.map { it.id })
    }

    @Test
    fun `children are not offered as parents`() {
        val all = listOf(testCategory(id = "a"), testCategory(id = "a1", parentId = "a"))

        assertEquals(listOf("a"), parentOptions(all, editing = null, type = expense).map { it.id })
    }

    @Test
    fun `categories of the other type are not offered`() {
        val all = listOf(testCategory(id = "a"), testCategory(id = "i", type = CategoryType.INCOME))

        assertEquals(listOf("a"), parentOptions(all, editing = null, type = expense).map { it.id })
    }
}
