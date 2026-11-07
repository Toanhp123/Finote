package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

fun parserRuleEntity(
    id: String = "rule-1",
    packageName: String = "com.test.bank",
    priority: Int = 0,
    channelIdAllowlist: String? = null,
    ignoreKeywords: String = "[\"OTP\"]",
) = ParserRuleEntity(
    id = id,
    packageName = packageName,
    bankName = "Test bank",
    channelIdAllowlist = channelIdAllowlist,
    ignoreKeywords = ignoreKeywords,
    priority = priority,
    isUserCustom = false,
    version = 1,
)

fun notificationLogEntity(
    id: String = "log-1",
    contentHash: String = "hash-1",
    status: NotificationStatus = NotificationStatus.PENDING,
) = NotificationLogEntity(
    id = id,
    packageName = "com.test.bank",
    channelId = "ch",
    title = "title",
    content = "content",
    contentHash = contentHash,
    postTime = 1_000L,
    receivedAt = 2_000L,
    matchedRuleId = null,
    parsedAmount = null,
    parsedType = null,
    parsedAccountMask = null,
    parsedPostBalance = null,
    parsedMerchant = null,
    status = status,
    failureReason = null,
    linkedTransactionId = null,
)
