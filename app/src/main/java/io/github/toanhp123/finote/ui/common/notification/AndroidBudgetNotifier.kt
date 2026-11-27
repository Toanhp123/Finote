package io.github.toanhp123.finote.ui.common.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.os.ConfigurationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.model.BudgetAlert
import io.github.toanhp123.finote.domain.budget.port.BudgetNotifier
import io.github.toanhp123.finote.ui.common.format.displayName
import javax.inject.Inject

class AndroidBudgetNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : BudgetNotifier {
        override fun notify(alert: BudgetAlert) {
            val res = localizedResources()
            val channelName = res.getString(R.string.budget_alert_channel)
            val manager = NotificationManagerCompat.from(context)
            manager.createNotificationChannel(
                NotificationChannel(BUDGET_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_DEFAULT),
            )
            if (!areBudgetAlertsEnabled(context)) return
            val name =
                if (alert.categoryId == null) {
                    res.getString(R.string.budget_total_title)
                } else {
                    res.displayName(alert.categoryName.orEmpty(), alert.categoryNameKey)
                }
            val id = "${alert.month}|${alert.categoryId ?: "total"}".hashCode()
            val publicVersion =
                NotificationCompat
                    .Builder(context, BUDGET_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_pie_chart)
                    .setContentTitle(channelName)
                    .build()
            val notification =
                NotificationCompat
                    .Builder(context, BUDGET_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_pie_chart)
                    .setContentTitle(channelName)
                    .setContentText(res.getString(R.string.budget_alert_text, name, alert.mark))
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .setPublicVersion(publicVersion)
                    .setContentIntent(openBudgetIntent(id))
                    .setAutoCancel(true)
                    .build()
            // Lint's MissingPermission needs this exact check around notify. Below 33 the permission does not exist.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(id, notification)
            }
        }

        // Below API 33 the application context ignores the per-app language, so apply it by hand.
        private fun localizedResources(): Resources {
            val locales = AppCompatDelegate.getApplicationLocales()
            if (locales.isEmpty) return context.resources
            val config = Configuration(context.resources.configuration)
            ConfigurationCompat.setLocales(config, locales)
            return context.createConfigurationContext(config).resources
        }

        private fun openBudgetIntent(requestCode: Int): PendingIntent {
            val launch =
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?: Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
            launch.putExtra(EXTRA_OPEN_TAB, OPEN_TAB_BUDGET).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                requestCode,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
