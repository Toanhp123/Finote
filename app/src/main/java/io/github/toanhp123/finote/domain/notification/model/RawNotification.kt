package io.github.toanhp123.finote.domain.notification.model

/** A notification as the listener read it, before any filtering. Its text is never logged. */
data class RawNotification(
    val packageName: String,
    val channelId: String?,
    val title: String,
    val content: String,
    val postTime: Long,
)
