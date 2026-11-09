package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision

/** Reads what the settlement planner needs and writes each decision in one atomic step. */
interface NotificationSettlementRepository {
    /** Logs with status `PENDING`, by `postTime`, then `id`. */
    suspend fun getPendingLogs(): List<NotificationLog>

    /** Logs that are neither `PENDING` nor `IGNORED_SPAM`, with `postTime >= since`. */
    suspend fun getSettledLogsSince(since: Long): List<NotificationLog>

    /** Every account row that has a `bankPackageName`. */
    suspend fun getAccounts(): List<Account>

    suspend fun getRulesById(): Map<String, ParserRule>

    /** One write transaction. Throws [IllegalStateException] when a log is no longer `PENDING` (all rolled back). */
    suspend fun book(
        decision: SettlementDecision.Book,
        transactionId: String,
        now: Long,
    )

    /**
     * Marks the log (and its partner) `FAILED` with the reason. True when at least one of the two rows changed;
     * false when none was `PENDING`.
     */
    suspend fun fail(decision: SettlementDecision.Fail): Boolean

    /** Marks the log (and its partner) `IGNORED_ACCOUNT`. True when at least one of the two rows changed. */
    suspend fun ignore(decision: SettlementDecision.Ignore): Boolean

    /** Marks the log `DEDUPLICATED` with this link. False when it was not `PENDING`. */
    suspend fun markRepost(
        logId: String,
        linkedTransactionId: String?,
    ): Boolean
}
