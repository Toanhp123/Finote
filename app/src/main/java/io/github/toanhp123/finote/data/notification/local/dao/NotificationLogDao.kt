package io.github.toanhp123.finote.data.notification.local.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import kotlinx.coroutines.flow.Flow

// Room needs constant SQL; this is INBOX_REASONS (domain) as a literal, and a test keeps the two equal.
private const val INBOX_REASONS_SQL =
    "('no_rule_matched', 'ambiguous_account', 'account_archived', 'invalid_amount', 'invalid_time', 'settle_error')"

@Dao
interface NotificationLogDao {
    /** Returns the new row id, or -1 when a row with the same `contentHash` exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: NotificationLogEntity): Long

    /** The newest [limit] logs, newest first; re-emits when the table changes. */
    @Query("SELECT * FROM notification_logs ORDER BY receivedAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationLogEntity>>

    @Query("SELECT * FROM notification_logs ORDER BY receivedAt ASC, id ASC")
    suspend fun getAll(): List<NotificationLogEntity>

    /** Logs a re-parse may change: `FAILED` / `no_rule_matched`, or `IGNORED_SPAM` hidden by a user keyword. */
    @Query(
        "SELECT * FROM notification_logs WHERE packageName IN (:packages) AND " +
            "((status = 'FAILED' AND failureReason = 'no_rule_matched') OR " +
            "(status = 'IGNORED_SPAM' AND failureReason LIKE 'user_keyword:%')) ORDER BY receivedAt ASC, id ASC",
    )
    suspend fun getReparseCandidates(packages: Set<String>): List<NotificationLogEntity>

    /**
     * Writes a re-parse outcome. Returns 0 when the row is gone or is no longer a re-parse candidate.
     * [isSecret] blanks title and content, as capture does.
     */
    @Suppress("LongParameterList") // why: one parameter per column the outcome writes
    @Query(
        "UPDATE notification_logs SET status = :status, failureReason = :failureReason, " +
            "matchedRuleId = :matchedRuleId, parsedAmount = :parsedAmount, parsedType = :parsedType, " +
            "parsedAccountMask = :parsedAccountMask, parsedPostBalance = :parsedPostBalance, " +
            "parsedMerchant = :parsedMerchant, parsedNote = :parsedNote, " +
            "title = CASE WHEN :isSecret THEN '' ELSE title END, " +
            "content = CASE WHEN :isSecret THEN '' ELSE content END " +
            "WHERE id = :id AND ((status = 'FAILED' AND failureReason = 'no_rule_matched') OR " +
            "(status = 'IGNORED_SPAM' AND failureReason LIKE 'user_keyword:%'))",
    )
    suspend fun updateFailedOutcome(
        id: String,
        isSecret: Boolean,
        status: NotificationStatus,
        failureReason: String?,
        matchedRuleId: String?,
        parsedAmount: Long?,
        parsedType: String?,
        parsedAccountMask: String?,
        parsedPostBalance: Long?,
        parsedMerchant: String?,
        parsedNote: String?,
    ): Int

    @Query("SELECT * FROM notification_logs WHERE status = 'PENDING' ORDER BY postTime ASC, id ASC")
    suspend fun getPending(): List<NotificationLogEntity>

    @Query(
        "SELECT * FROM notification_logs WHERE status NOT IN ('PENDING', 'IGNORED_SPAM') AND postTime >= :since " +
            "ORDER BY postTime ASC, id ASC",
    )
    suspend fun getSettledSince(since: Long): List<NotificationLogEntity>

    /** Moves the rows that are still `PENDING` to [status]; returns how many changed. */
    @Query(
        "UPDATE notification_logs SET status = :status, linkedTransactionId = :linkedTransactionId, " +
            "failureReason = :failureReason WHERE id IN (:ids) AND status = 'PENDING'",
    )
    suspend fun markSettled(
        ids: List<String>,
        status: NotificationStatus,
        linkedTransactionId: String?,
        failureReason: String?,
    ): Int

    /** `CONFIRMED` to `PAIRED_TRANSFER`, link unchanged; returns how many rows changed. */
    @Query("UPDATE notification_logs SET status = 'PAIRED_TRANSFER' WHERE id = :id AND status = 'CONFIRMED'")
    suspend fun markPairedTransfer(id: String): Int

    /** `PAIRED_TRANSFER` back to `CONFIRMED`, link unchanged; returns how many rows changed. */
    @Query("UPDATE notification_logs SET status = 'CONFIRMED' WHERE id IN (:ids) AND status = 'PAIRED_TRANSFER'")
    suspend fun markUnpaired(ids: List<String>): Int

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

    /**
     * `FAILED` inbox rows to `CONFIRMED`; returns how many changed. An unparsed row takes the booked [amount] and
     * [type], so repost checks can match it; a parsed row keeps what its rule read.
     * shortcut: an amount or type the user changed in the sheet no longer matches the text, so a repost is not
     * recognized; upgrade: store the text's own amount beside the booked one.
     */
    @Query(
        "UPDATE notification_logs SET status = 'CONFIRMED', linkedTransactionId = :linkedTransactionId, " +
            "parsedType = CASE WHEN parsedAmount IS NULL OR parsedType IS NULL THEN :type ELSE parsedType END, " +
            "parsedAmount = CASE WHEN parsedAmount IS NULL OR parsedType IS NULL THEN :amount ELSE parsedAmount END " +
            "WHERE id IN (:ids) AND status = 'FAILED' AND failureReason IN $INBOX_REASONS_SQL",
    )
    suspend fun confirmFromInbox(
        ids: List<String>,
        linkedTransactionId: String,
        amount: Long,
        type: String,
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
