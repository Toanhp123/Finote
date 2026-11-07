package io.github.toanhp123.finote.data.notification

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

/** System log, not user data, so it has no `deletedAt`. The `parsed*` columns are filled from P2b on. */
@Entity(
    tableName = "notification_logs",
    indices = [Index(value = ["packageName"]), Index(value = ["contentHash"], unique = true)],
)
data class NotificationLogEntity(
    @PrimaryKey val id: String,
    val packageName: String,
    val channelId: String?,
    /** Empty for an `IGNORED_SPAM` row. */
    val title: String,
    /** Empty for an `IGNORED_SPAM` row. */
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
    val status: NotificationStatus,
    val failureReason: String?,
    val linkedTransactionId: String?,
)
