package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class SetupNavigationTest {
    @Test
    fun `finishing onboarding replaces the whole stack with overview`() {
        val key = Setup(isFromOnboarding = true)
        val stack = mutableListOf<NavKey>(key)

        finishSetup(stack, key)

        assertEquals(listOf<NavKey>(Overview), stack)
    }

    @Test
    fun `opening setup twice leaves one setup on top`() {
        val stack = mutableListOf<NavKey>(Overview, Transactions)

        openSetup(stack)
        openSetup(stack)

        assertEquals(listOf<NavKey>(Overview, Transactions, Setup(isFromOnboarding = false)), stack)
    }

    @Test
    fun `finishing from the banner returns to the previous screen`() {
        val key = Setup(isFromOnboarding = false)
        val stack = mutableListOf<NavKey>(Overview, Transactions, key)

        finishSetup(stack, key)

        assertEquals(listOf<NavKey>(Overview, Transactions), stack)
    }
}
