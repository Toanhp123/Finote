package io.github.toanhp123.finote.data.notification

import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.local.entity.ParserRuleEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

// why: test fixture with one named default per entity field the tests vary.
@Suppress("LongParameterList")
fun parserRuleEntity(
    id: String = "rule-1",
    packageName: String = "com.test.bank",
    priority: Int = 0,
    channelIdAllowlist: String? = null,
    ignoreKeywords: String = "[\"OTP\"]",
    regexPattern: String = "",
    fallbackTransactionType: String? = null,
    isActive: Boolean = true,
    accountType: String? = null,
) = ParserRuleEntity(
    id = id,
    packageName = packageName,
    bankName = "Test bank",
    channelIdAllowlist = channelIdAllowlist,
    ignoreKeywords = ignoreKeywords,
    priority = priority,
    isUserCustom = false,
    version = 1,
    regexPattern = regexPattern,
    fallbackTransactionType = fallbackTransactionType,
    dateFormatPattern = null,
    isActive = isActive,
    updatedAt = 0L,
    accountType = accountType,
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
    parsedNote = null,
    status = status,
    failureReason = null,
    linkedTransactionId = null,
)
