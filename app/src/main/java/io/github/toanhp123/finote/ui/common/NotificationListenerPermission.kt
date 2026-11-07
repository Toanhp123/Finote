package io.github.toanhp123.finote.ui.common

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Whether the user has turned on Notification Listener access for this app. */
fun isNotificationListenerEnabled(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** The access state, read again on every resume, because the user changes it in the system settings. */
@Composable
fun rememberNotificationListenerEnabled(): Boolean {
    val context = LocalContext.current
    var isEnabled by remember { mutableStateOf(isNotificationListenerEnabled(context)) }
    LifecycleResumeEffect(context) {
        isEnabled = isNotificationListenerEnabled(context)
        onPauseOrDispose {}
    }
    return isEnabled
}
