package io.github.toanhp123.finote.automation

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dagger.hilt.android.AndroidEntryPoint
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.service.notificationContent
import io.github.toanhp123.finote.domain.notification.usecase.CaptureNotificationUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/** Reads a notification into a [RawNotification] and hands it to the capture use case. Nothing more. */
@AndroidEntryPoint
class FinoteNotificationListener : NotificationListenerService() {
    @Inject
    lateinit var capture: CaptureNotificationUseCase

    // A failed notification is dropped; the next onListenerConnected rescan retries it.
    // Main, not IO: Room suspend calls leave the main thread on their own.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, _ -> })

    // A fair mutex is FIFO, so notifications are stored in the order they arrived.
    private val mutex = Mutex()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch { process(sbn) }
    }

    override fun onListenerConnected() {
        // Some ROMs throw from activeNotifications while the service is closing. One launch per notification, so a
        // failing one cannot stop the rest of the rescan.
        runCatching { activeNotifications }.getOrNull().orEmpty().forEach { scope.launch { process(it) } }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun process(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val skipFlags = Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE
        if (sbn.packageName == packageName || sbn.isOngoing || (notification.flags and skipFlags) != 0) return
        val extras = notification.extras
        val content =
            notificationContent(
                bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT),
                textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES),
                text = extras.getCharSequence(Notification.EXTRA_TEXT),
            ) ?: return
        val raw =
            RawNotification(
                packageName = sbn.packageName,
                channelId = notification.channelId,
                title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
                content = content,
                postTime = sbn.postTime,
            )
        mutex.withLock { capture(raw) }
    }
}
