package io.github.toanhp123.finote.domain.notification.model

/** Verdict of the capture filter and parser for one notification. [isSecret] means the text must not be stored. */
data class Classification(
    val status: NotificationStatus,
    val failureReason: String?,
    val parsed: ParseResult.Parsed?,
    val isSecret: Boolean,
)
