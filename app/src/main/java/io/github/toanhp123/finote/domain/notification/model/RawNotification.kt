package io.github.toanhp123.finote.domain.notification.model

/** A notification as the listener read it, before any filtering. Its text is never logged. */
data class RawNotification(
    val packageName: String,
    val channelId: String?,
    val title: String,
    val content: String,
    val postTime: Long,
    /** The platform's `StatusBarNotification.key`; it names the same posting again when the app updates it. */
    val notificationKey: String? = null,
) {
    // Text and key stay out of logs and crash reports.
    override fun toString() = "RawNotification(packageName=$packageName, postTime=$postTime)"
}
