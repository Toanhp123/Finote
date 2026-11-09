package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

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
        linkedTransactionId = linkedTransactionId,
    )

/** [ParsedFields] is restored only when amount and a known type are both stored. */
fun NotificationLogEntity.toDomain(): NotificationLog {
    val type = TransactionType.entries.firstOrNull { it.name == parsedType }
    return NotificationLog(
        id = id,
        packageName = packageName,
        channelId = channelId,
        title = title,
        content = content,
        contentHash = contentHash,
        postTime = postTime,
        receivedAt = receivedAt,
        status = status,
        failureReason = failureReason,
        matchedRuleId = matchedRuleId,
        parsed =
            if (parsedAmount != null && type != null) {
                ParsedFields(parsedAmount, type, parsedAccountMask, parsedPostBalance, parsedMerchant, parsedNote)
            } else {
                null
            },
        linkedTransactionId = linkedTransactionId,
    )
}
