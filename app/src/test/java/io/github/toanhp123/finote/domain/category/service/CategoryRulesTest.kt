package io.github.toanhp123.finote.domain.category.service

import io.github.toanhp123.finote.domain.category.error.CategoryError
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryRulesTest {
    private val top = testCategory(id = "top")

    // checkNew

    @Test
    fun `new top-level category is allowed`() = assertNull(CategoryRules.checkNew(testCategory(), parent = null))

    @Test
    fun `new child of a live top-level parent of the same type is allowed`() =
        assertNull(CategoryRules.checkNew(testCategory(parentId = "top"), top))

    @Test
    fun `new blank name is BlankName`() {
        assertEquals(CategoryError.BlankName, CategoryRules.checkNew(testCategory(name = ""), null))
        assertEquals(CategoryError.BlankName, CategoryRules.checkNew(testCategory(name = "   "), null))
    }

    @Test
    fun `new blank name wins over a bad parent`() =
        assertEquals(CategoryError.BlankName, CategoryRules.checkNew(testCategory(name = " ", parentId = "x"), null))

    @Test
    fun `new with a parentId but no stored parent is InvalidParent`() =
        assertEquals(CategoryError.InvalidParent, CategoryRules.checkNew(testCategory(parentId = "ghost"), null))

    @Test
    fun `new with a soft-deleted parent is InvalidParent`() =
        assertEquals(
            CategoryError.InvalidParent,
            CategoryRules.checkNew(testCategory(parentId = "top"), top.copy(deletedAt = 5L)),
        )

    @Test
    fun `new with a child as parent is InvalidParent`() =
        assertEquals(
            CategoryError.InvalidParent,
            CategoryRules.checkNew(testCategory(parentId = "top"), top.copy(parentId = "root")),
        )

    @Test
    fun `new with a parent of the other type is InvalidParent`() =
        assertEquals(
            CategoryError.InvalidParent,
            CategoryRules.checkNew(testCategory(parentId = "top"), top.copy(type = CategoryType.INCOME)),
        )

    @Test
    fun `new that is its own parent is InvalidParent`() =
        assertEquals(
            CategoryError.InvalidParent,
            CategoryRules.checkNew(testCategory(id = "top", parentId = "top"), top),
        )

    // checkUpdate

    private fun checkUpdate(
        new: Category,
        old: Category = testCategory(),
        parent: Category? = null,
        liveChildren: Int = 0,
    ) = CategoryRules.checkUpdate(old, new, parent, liveChildren)

    @Test
    fun `update that renames is allowed`() = assertNull(checkUpdate(testCategory(name = "Drinks")))

    @Test
    fun `update giving a parent to a childless category is allowed`() =
        assertNull(checkUpdate(testCategory(parentId = "top"), parent = top))

    @Test
    fun `update changing type is TypeChanged`() =
        assertEquals(CategoryError.TypeChanged, checkUpdate(testCategory(type = CategoryType.INCOME)))

    @Test
    fun `update changing the parent of a system category is SystemParentChanged`() {
        val system = testCategory(parentId = "top").copy(isSystemDefault = true)

        assertEquals(CategoryError.SystemParentChanged, checkUpdate(system.copy(parentId = "other"), old = system))
        assertEquals(CategoryError.SystemParentChanged, checkUpdate(system.copy(parentId = null), old = system))
    }

    @Test
    fun `update keeping the parent of a system category is allowed`() {
        val system = testCategory(parentId = "top").copy(isSystemDefault = true)

        assertNull(checkUpdate(system.copy(name = "Renamed"), old = system, parent = top))
    }

    @Test
    fun `update with a parentId but no stored parent is InvalidParent`() =
        assertEquals(CategoryError.InvalidParent, checkUpdate(testCategory(parentId = "ghost")))

    @Test
    fun `update that is its own parent is InvalidParent`() {
        val self = testCategory(id = "cat-1", parentId = "cat-1")

        assertEquals(CategoryError.InvalidParent, checkUpdate(self, parent = testCategory(id = "cat-1")))
    }

    @Test
    fun `update with a deleted, nested or other-type parent is InvalidParent`() {
        val new = testCategory(parentId = "top")

        assertEquals(CategoryError.InvalidParent, checkUpdate(new, parent = top.copy(deletedAt = 5L)))
        assertEquals(CategoryError.InvalidParent, checkUpdate(new, parent = top.copy(parentId = "root")))
        assertEquals(CategoryError.InvalidParent, checkUpdate(new, parent = top.copy(type = CategoryType.INCOME)))
    }

    @Test
    fun `update giving a parent to a category with live children is HasChildren`() =
        assertEquals(
            CategoryError.HasChildren,
            checkUpdate(testCategory(parentId = "top"), parent = top, liveChildren = 1),
        )

    @Test
    fun `update of a top-level category with live children is allowed`() =
        assertNull(checkUpdate(testCategory(name = "Drinks"), liveChildren = 2))

    @Test
    fun `update with a blank name is BlankName`() =
        assertEquals(CategoryError.BlankName, checkUpdate(testCategory(name = " ")))

    @Test
    fun `update error order is type, system parent, parent, children, name`() {
        val system = testCategory(parentId = "top").copy(isSystemDefault = true)
        val everythingWrong = system.copy(type = CategoryType.INCOME, parentId = "x", name = " ")

        assertEquals(CategoryError.TypeChanged, checkUpdate(everythingWrong, old = system, liveChildren = 1))
        assertEquals(
            CategoryError.SystemParentChanged,
            checkUpdate(everythingWrong.copy(type = system.type), old = system, liveChildren = 1),
        )
        assertEquals(
            CategoryError.InvalidParent,
            checkUpdate(testCategory(parentId = "x", name = " "), liveChildren = 1),
        )
        assertEquals(
            CategoryError.HasChildren,
            checkUpdate(testCategory(parentId = "top", name = " "), parent = top, liveChildren = 1),
        )
    }

    // checkDelete

    @Test
    fun `delete of a plain category is allowed`() = assertNull(CategoryRules.checkDelete(testCategory(), 0))

    @Test
    fun `delete of a system category is SystemCategory`() {
        val system = testCategory().copy(isSystemDefault = true)

        assertEquals(CategoryError.SystemCategory, CategoryRules.checkDelete(system, 0))
        assertEquals(CategoryError.SystemCategory, CategoryRules.checkDelete(system, 3))
    }

    @Test
    fun `delete of a parent with live children is HasChildren`() =
        assertEquals(CategoryError.HasChildren, CategoryRules.checkDelete(testCategory(), 1))
}
