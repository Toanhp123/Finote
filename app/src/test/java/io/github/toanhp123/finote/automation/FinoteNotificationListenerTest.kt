package io.github.toanhp123.finote.automation

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], application = Application::class)
class FinoteNotificationListenerTest {
    @Test
    fun `rebind leaves the listener component enabled`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        FinoteNotificationListener.rebindIfDisconnected(context)
        val state =
            context.packageManager.getComponentEnabledSetting(
                ComponentName(context, FinoteNotificationListener::class.java),
            )
        assertEquals(COMPONENT_ENABLED_STATE_ENABLED, state)
    }
}
