package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.NotificationLog

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
        matchedRuleId = matchedRuleId,
        parsedAmount = parsed?.amount,
        parsedType = parsed?.type?.name,
        parsedAccountMask = parsed?.accountMask,
        parsedPostBalance = parsed?.postBalance,
        parsedMerchant = parsed?.merchant,
        parsedNote = parsed?.note,
        status = status,
        failureReason = failureReason,
        linkedTransactionId = null,
    )
