package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.service.contentHash
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

// why: test builders, every parameter is one knob of the model
@Suppress("LongParameterList")
fun testRule(
    id: String = "r1",
    packageName: String = "pkg",
    bankName: String = "Bank",
    channelIdAllowlist: List<String>? = null,
    ignoreKeywords: List<String> = emptyList(),
    priority: Int = 0,
    isUserCustom: Boolean = false,
    version: Int = 1,
    regexPattern: String = ".*",
    fallbackTransactionType: TransactionType? = null,
    dateFormatPattern: String? = null,
    isActive: Boolean = true,
    updatedAt: Long = 0L,
    accountType: AccountType? = null,
) = ParserRule(
    id = id,
    packageName = packageName,
    bankName = bankName,
    channelIdAllowlist = channelIdAllowlist,
    ignoreKeywords = ignoreKeywords,
    priority = priority,
    isUserCustom = isUserCustom,
    version = version,
    regexPattern = regexPattern,
    fallbackTransactionType = fallbackTransactionType,
    dateFormatPattern = dateFormatPattern,
    isActive = isActive,
    updatedAt = updatedAt,
    accountType = accountType,
)

// why: test builders, every parameter is one knob of the model
@Suppress("LongParameterList")
fun testLog(
    id: String = "log1",
    packageName: String = "pkg",
    channelId: String? = null,
    title: String = "title",
    content: String = "content",
    contentHash: String = id,
    postTime: Long = 1_000L,
    receivedAt: Long = postTime,
    status: NotificationStatus = NotificationStatus.PENDING,
    failureReason: String? = null,
    matchedRuleId: String? = null,
    parsed: ParsedFields? = null,
    linkedTransactionId: String? = null,
) = NotificationLog(
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
    parsed = parsed,
    linkedTransactionId = linkedTransactionId,
)

fun testRaw(
    packageName: String = "pkg",
    channelId: String? = "ch",
    title: String = "title",
    content: String = "content",
    postTime: Long = 1_000L,
) = RawNotification(packageName, channelId, title, content, postTime)
