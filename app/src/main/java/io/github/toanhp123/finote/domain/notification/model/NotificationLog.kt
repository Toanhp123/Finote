package io.github.toanhp123.finote.domain.notification.model

/**
 * One captured notification. Only a secret (OTP and the like) drops the text: title and content are empty.
 * [matchedRuleId] and [parsed] are filled when a rule matches.
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
    val matchedRuleId: String? = null,
    val parsed: ParsedFields? = null,
)
