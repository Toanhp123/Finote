package io.github.toanhp123.finote.ui.common

import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37])
class NotificationListenerPermissionTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun `reports access on the first resume`() {
        val results = mutableListOf<Boolean>()

        rule.setContent { ReportListenerAccess { results += it } }
        rule.waitForIdle()

        assertEquals(listOf(false), results)
    }

    @Test
    fun `opens the listener settings`() {
        // Callers pass an Activity (LocalContext), which needs no NEW_TASK flag.
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        openListenerSettings(activity)

        assertEquals(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            shadowOf(activity).nextStartedActivity.action,
        )
    }
}
