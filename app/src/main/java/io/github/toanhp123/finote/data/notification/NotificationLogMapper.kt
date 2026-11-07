package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.NotificationLog

/** The parsed columns stay null until P2b. */
fun NotificationLog.toEntity(): NotificationLogEntity =
    NotificationLogEntity(
        id = id,
        packageName = packageName,
        channelId = channelId,
        title = title,
        content = content,
        contentHash = contentHash,
        postTime = postTime,
        receivedAt = receivedAt,
        matchedRuleId = null,
        parsedAmount = null,
        parsedType = null,
        parsedAccountMask = null,
        parsedPostBalance = null,
        parsedMerchant = null,
        status = status,
        failureReason = failureReason,
        linkedTransactionId = null,
    )
