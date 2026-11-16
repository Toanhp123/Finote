package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EditTransactionNavigationTest {
    @Test
    fun `opening an editor twice keeps the first one`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions)

        openEditor(stack, "a")
        openEditor(stack, "b")

        assertEquals(listOf<NavKey>(Overview, Transactions, EditTransaction("a")), stack)
    }

    @Test
    fun `closing an editor twice is harmless`() {
        val key = EditTransaction("a")
        val stack = mutableListOf<NavKey>(Overview, Transactions, key)

        closeEditor(stack, key)
        closeEditor(stack, key)

        assertEquals(listOf<NavKey>(Overview, Transactions), stack)
    }

    @Test
    fun `an editor opens again for another id after closing`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions)

        openEditor(stack, "a")
        closeEditor(stack, EditTransaction("a"))
        openEditor(stack, "b")

        assertEquals(EditTransaction("b"), stack.last())
    }

    @Test
    fun `the tab bar stays under an editor on a tab`() {
        assertTrue(isTabBarVisible(listOf(Overview, Transactions, EditTransaction("a"))))
        assertTrue(isTabBarVisible(listOf(Overview)))
    }

    @Test
    fun `the tab bar hides on a pushed page`() {
        assertFalse(isTabBarVisible(listOf(Overview, Settings, AccountsSettings)))
    }
}
