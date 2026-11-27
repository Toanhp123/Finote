package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavBackStackTest {
    @Test
    fun `alert opens budget from another tab`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions)

        openTabFromAlert(stack)

        assertEquals(listOf<NavKey>(Overview, Budget), stack)
    }

    @Test
    fun `alert closes a sheet above budget`() {
        val stack = mutableListOf<NavKey>(Overview, Budget, EditTransaction("t1"))

        openTabFromAlert(stack)

        assertEquals(listOf<NavKey>(Overview, Budget), stack)
    }

    @Test
    fun `alert does nothing during onboarding setup`() {
        val stack = mutableListOf<NavKey>(Setup(isFromOnboarding = true))

        openTabFromAlert(stack)

        assertEquals(listOf<NavKey>(Setup(isFromOnboarding = true)), stack)
    }

    @Test
    fun `alert on budget itself does nothing`() {
        val stack = mutableListOf<NavKey>(Overview, Budget)

        openTabFromAlert(stack)

        assertEquals(listOf<NavKey>(Overview, Budget), stack)
    }
}
