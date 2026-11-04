package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelTabTest {
    private data object Child : NavKey

    @Test
    fun `overview back stack is overview alone`() {
        assertEquals(listOf<NavKey>(Overview), backStackFor(Overview))
    }

    @Test
    fun `other tab sits on top of overview`() {
        assertEquals(listOf<NavKey>(Overview, Budget), backStackFor(Budget))
    }

    @Test
    fun `current tab is the last top-level key`() {
        assertEquals(Transactions, currentTab(listOf(Overview, Transactions)))
    }

    @Test
    fun `current tab skips keys that are not tabs`() {
        assertEquals(Transactions, currentTab(listOf(Overview, Transactions, Child)))
    }

    @Test
    fun `current tab of an empty stack is overview`() {
        assertEquals(Overview, currentTab(emptyList()))
    }

    @Test
    fun `picking the current tab leaves the stack untouched`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions, Child)

        selectTab(stack, Transactions)

        assertEquals(listOf<NavKey>(Overview, Transactions, Child), stack)
    }

    @Test
    fun `picking another tab replaces the tab on top`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions)

        selectTab(stack, Budget)

        assertEquals(listOf<NavKey>(Overview, Budget), stack)
    }

    @Test
    fun `picking overview leaves overview alone`() {
        val stack = mutableListOf<NavKey>(Overview, Budget)

        selectTab(stack, Overview)

        assertEquals(listOf<NavKey>(Overview), stack)
    }

    @Test
    fun `back from a tab returns to overview`() {
        val stack = mutableListOf<NavKey>(Overview, Budget)

        stack.removeLastOrNull()

        assertEquals(listOf<NavKey>(Overview), stack)
        assertEquals(Overview, currentTab(stack))
    }
}
