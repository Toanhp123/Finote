package io.github.toanhp123.finote.data.notification.local.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

/** System log, not user data, so it has no `deletedAt`. `parsedType` is `TransactionType.name`. */
@Entity(
    tableName = "notification_logs",
    indices = [Index(value = ["packageName"]), Index(value = ["contentHash"], unique = true)],
)
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val channelId: String?,
    /** Empty only for an `IGNORED_SPAM` row whose `failureReason` starts with `secret:`; other rows keep the text. */
    val title: String,
    /** Empty only for an `IGNORED_SPAM` row whose `failureReason` starts with `secret:`; other rows keep the text. */
    val content: String,
    val contentHash: String,
    val postTime: Long,
    val receivedAt: Long,
    val matchedRuleId: String?,
    val parsedAmount: Long?,
    val parsedType: String?,
    val parsedAccountMask: String?,
    val parsedPostBalance: Long?,
    val parsedMerchant: String?,
    val parsedNote: String?,
    val status: NotificationStatus,
    val failureReason: String?,
    val linkedTransactionId: String?,
)
