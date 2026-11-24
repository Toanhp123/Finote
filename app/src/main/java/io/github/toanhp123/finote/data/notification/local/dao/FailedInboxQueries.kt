package io.github.toanhp123.finote.data.notification.local.dao

import androidx.room3.Query
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import kotlinx.coroutines.flow.Flow

// Room needs constant SQL; this is INBOX_REASONS (domain) as a literal, and a test keeps the two equal.
private const val INBOX_REASONS_SQL =
    "('no_rule_matched', 'ambiguous_account', 'account_archived', 'invalid_amount', 'invalid_time', 'settle_error')"

/** The failed inbox queries, inherited by [NotificationLogDao]; apart from it for the detekt function limit. */
interface FailedInboxQueries {
    /** Inbox candidates: `FAILED` with a reason the inbox may show, or `IGNORED_SPAM` hidden by a user keyword. */
    @Query(
        "SELECT * FROM notification_logs WHERE (status = 'FAILED' AND failureReason IN $INBOX_REASONS_SQL) OR " +
            "(status = 'IGNORED_SPAM' AND failureReason LIKE 'user_keyword:%') ORDER BY postTime DESC, id DESC",
    )
    fun observeInboxLogs(): Flow<List<NotificationLogEntity>>

    /** Package, key, time and parsed amount of the booked logs that carry one of [keys]. */
    @Query(
        "SELECT packageName, notificationKey, postTime, parsedAmount FROM notification_logs " +
            "WHERE status IN ('CONFIRMED', 'PAIRED_TRANSFER', 'DEDUPLICATED') AND notificationKey IN (:keys)",
    )
    suspend fun getHandledKeys(keys: List<String>): List<KeyRow>

    @Query("SELECT * FROM notification_logs WHERE id = :id")
    suspend fun getById(id: String): NotificationLogEntity?

    /** The identical `no_rule_matched` copies of one notification text in a package. */
    @Query(
        "SELECT * FROM notification_logs WHERE status = 'FAILED' AND failureReason = 'no_rule_matched' AND " +
            "packageName = :packageName AND title = :title AND content = :content",
    )
    suspend fun getUnparsedCopies(
        packageName: String,
        title: String,
        content: String,
    ): List<NotificationLogEntity>

    /** `FAILED` inbox rows to `CONFIRMED`; returns how many changed. */
    @Query(
        "UPDATE notification_logs SET status = 'CONFIRMED', linkedTransactionId = :linkedTransactionId " +
            "WHERE id IN (:ids) AND status = 'FAILED' AND failureReason IN $INBOX_REASONS_SQL",
    )
    suspend fun confirmFromInbox(
        ids: List<String>,
        linkedTransactionId: String,
    ): Int

    /** `FAILED` inbox rows keep `FAILED` and get reason `user_dismissed`; returns how many changed. */
    @Query(
        "UPDATE notification_logs SET failureReason = 'user_dismissed' " +
            "WHERE id IN (:ids) AND status = 'FAILED' AND failureReason IN $INBOX_REASONS_SQL",
    )
    suspend fun dismissFromInbox(ids: List<String>): Int

    @Query("SELECT * FROM notification_logs WHERE packageName = :packageName ORDER BY receivedAt ASC, id ASC")
    suspend fun getByPackage(packageName: String): List<NotificationLogEntity>

    /** `FAILED` / `no_rule_matched` rows to `IGNORED_SPAM` with [reason]; rows that moved on stay; returns how many. */
    @Query(
        "UPDATE notification_logs SET status = 'IGNORED_SPAM', failureReason = :reason " +
            "WHERE id IN (:ids) AND status = 'FAILED' AND failureReason = 'no_rule_matched'",
    )
    suspend fun hideWithKeyword(
        ids: List<String>,
        reason: String,
    ): Int

    /** The package's `IGNORED_SPAM` rows with exactly [reason] back to `FAILED` / `no_rule_matched`. */
    @Query(
        "UPDATE notification_logs SET status = 'FAILED', failureReason = 'no_rule_matched' " +
            "WHERE packageName = :packageName AND status = 'IGNORED_SPAM' AND failureReason = :reason",
    )
    suspend fun restoreKeyword(
        packageName: String,
        reason: String,
    ): Int
}
