package io.github.toanhp123.finote.ui.settings.categoryedit.logic

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CategoryDraftTest {
    private val system = testCategory(id = "s", name = "Food").copy(nameKey = "sys_food", isSystemDefault = true)

    @Test
    fun `an untouched name sends the stored name, so nameKey survives`() {
        val saved = system.toDraft().toCategory(system, type = CategoryType.EXPENSE)

        assertEquals("Food", saved.name)
        assertEquals("sys_food", saved.nameKey)
    }

    @Test
    fun `edited name is trimmed and sent`() {
        val draft = system.toDraft().copy(name = "  Cafe  ")

        assertEquals("Cafe", draft.toCategory(system, type = CategoryType.EXPENSE).name)
    }

    @Test
    fun `the draft starts from the stored name`() {
        assertEquals("Food", system.toDraft().name)
    }

    @Test
    fun `a new category is a user row with the typed fields`() {
        val draft = CategoryDraft(name = " Gym ", parentId = "p", icon = "fitness_center", color = 7)

        val created = draft.toCategory(stored = null, type = CategoryType.INCOME)

        assertEquals("Gym", created.name)
        assertEquals(CategoryType.INCOME, created.type)
        assertEquals("p", created.parentId)
        assertEquals("fitness_center", created.icon)
        assertEquals(7, created.color)
        assertNull(created.nameKey)
        assertFalse(created.isSystemDefault)
        assertFalse(created.isHidden)
    }
}
