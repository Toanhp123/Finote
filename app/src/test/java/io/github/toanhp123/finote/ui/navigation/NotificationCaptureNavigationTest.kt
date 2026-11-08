package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCaptureNavigationTest {
    @Test
    fun `opening the capture page twice leaves one copy on top`() {
        val stack = mutableListOf<NavKey>(Overview, Settings)

        openNotificationCaptureSettings(stack)
        openNotificationCaptureSettings(stack)

        assertEquals(listOf<NavKey>(Overview, Settings, NotificationCaptureSettings), stack)
    }
}
