package io.github.toanhp123.finote.data.notification

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus

@Dao
interface NotificationLogDao {
    /** Returns the new row id, or -1 when a row with the same `contentHash` exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(log: NotificationLogEntity): Long

    @Query("SELECT * FROM notification_logs ORDER BY receivedAt ASC, id ASC")
    suspend fun getAll(): List<NotificationLogEntity>

    @Query(
        "SELECT * FROM notification_logs WHERE status = 'FAILED' AND failureReason = 'no_rule_matched' " +
            "AND packageName IN (:packages) ORDER BY receivedAt ASC, id ASC",
    )
    suspend fun getFailed(packages: Set<String>): List<NotificationLogEntity>

    /**
     * Writes a re-parse outcome. Returns 0 when the row is gone or is no longer `FAILED`.
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
            "WHERE id = :id AND status = 'FAILED'",
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
}
