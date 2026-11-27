package io.github.toanhp123.finote.ui.common.notification

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** Intent extra that asks the app to open a tab. It lives here because `ui/common` may not import `ui/navigation`. */
const val EXTRA_OPEN_TAB = "open_tab"
const val OPEN_TAB_BUDGET = "budget"

internal const val BUDGET_CHANNEL_ID = "budget"

/** Whether a budget alert can show: notifications are on and the budget channel (once created) is not turned off. */
fun areBudgetAlertsEnabled(context: Context): Boolean {
    val manager = NotificationManagerCompat.from(context)
    val channel = manager.getNotificationChannel(BUDGET_CHANNEL_ID)
    return manager.areNotificationsEnabled() && channel?.importance != NotificationManager.IMPORTANCE_NONE
}

/** Opens the system notification settings of this app. */
fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
