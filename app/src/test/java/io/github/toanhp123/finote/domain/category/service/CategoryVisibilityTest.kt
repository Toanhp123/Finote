package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryVisibilityTest {
    private val parent = testCategory(id = "p")

    @Test
    fun `a live visible top-level category is shown`() = assertTrue(CategoryVisibility.isShown(parent, null))

    @Test
    fun `a hidden category is not shown`() = assertFalse(CategoryVisibility.isShown(parent.copy(isHidden = true), null))

    @Test
    fun `a visible child of a hidden parent is not shown`() =
        assertFalse(CategoryVisibility.isShown(testCategory(id = "c", parentId = "p"), parent.copy(isHidden = true)))

    @Test
    fun `a deleted category is not shown`() = assertFalse(CategoryVisibility.isShown(parent.copy(deletedAt = 5L), null))

    @Test
    fun `shown keeps order and drops hidden branches`() {
        val p = parent.copy(isHidden = true)
        val c1 = testCategory(id = "c1", parentId = "p")
        val q = testCategory(id = "q")
        val c2 = testCategory(id = "c2", parentId = "q").copy(isHidden = true)

        assertEquals(listOf(q), listOf(p, c1, q, c2).shown())
    }
}
