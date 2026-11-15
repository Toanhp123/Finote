package io.github.toanhp123.finote.ui.cashentry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// The Activity is a Hilt entry point and the project has no Hilt test setup, so the decision is a pure function.
class CashEntryActivityTest {
    @Test
    fun `another row restarts the sheet`() {
        assertTrue(CashEntryActivity.shouldRestartFor(current = "a", incoming = "b"))
    }

    @Test
    fun `the same row keeps the sheet`() {
        assertFalse(CashEntryActivity.shouldRestartFor(current = "a", incoming = "a"))
    }

    @Test
    fun `a second create launch keeps the sheet`() {
        assertFalse(CashEntryActivity.shouldRestartFor(current = null, incoming = null))
    }

    @Test
    fun `a create launch restarts an edit sheet`() {
        assertTrue(CashEntryActivity.shouldRestartFor(current = "a", incoming = null))
    }

    @Test
    fun `an edit launch restarts a create sheet`() {
        assertTrue(CashEntryActivity.shouldRestartFor(current = null, incoming = "b"))
    }
}
