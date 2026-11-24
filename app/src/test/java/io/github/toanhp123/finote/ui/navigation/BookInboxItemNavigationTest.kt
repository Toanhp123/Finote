package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class BookInboxItemNavigationTest {
    @Test
    fun `opening a booking sheet twice keeps the first one`() {
        val stack = mutableListOf<NavKey>(Overview, Settings, FailedInbox)

        openBooking(stack, "a")
        openBooking(stack, "b")

        assertEquals(listOf<NavKey>(Overview, Settings, FailedInbox, BookInboxItem("a")), stack)
    }
}
