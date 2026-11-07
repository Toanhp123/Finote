package io.github.toanhp123.finote.domain.notification.model

/**
 * One captured notification. An `IGNORED_SPAM` row keeps no text: title and content are empty.
 * P2b adds the parsed fields.
 */
data class NotificationLog(
    val id: String,
    val packageName: String,
    val channelId: String?,
    val title: String,
    val content: String,
    val contentHash: String,
    val postTime: Long,
    val receivedAt: Long,
    val status: NotificationStatus,
    val failureReason: String?,
)
