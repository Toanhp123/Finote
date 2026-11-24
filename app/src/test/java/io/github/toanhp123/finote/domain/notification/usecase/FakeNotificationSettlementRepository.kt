package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.notification.error.SettlementError
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.service.FAIL_PARTNER_FAILED

/**
 * Copies from the real repository: log filters and order, and each write moves only `PENDING` logs out of `PENDING`.
 * Does not copy: transaction or balance writes, and stale-account checks (use [bookRejections]).
 */
class FakeNotificationSettlementRepository(
    val logs: MutableList<NotificationLog> = mutableListOf(),
    private val accounts: List<Account> = emptyList(),
    private val identifiers: List<AccountIdentifier> = emptyList(),
    private val rules: Map<String, ParserRule> = emptyMap(),
) : NotificationSettlementRepository {
    /** Bank side log id -> error thrown by [book] before anything is written. */
    val bookErrors = mutableMapOf<String, Throwable>()

    /** Bank side log id -> error returned by [book] before anything is written (stale data). */
    val bookRejections = mutableMapOf<String, SettlementError>()

    /** Log id -> error thrown by [fail] before anything is written. */
    val failErrors = mutableMapOf<String, Throwable>()

    /** Log id -> error thrown by [ignore] before anything is written. */
    val ignoreErrors = mutableMapOf<String, Throwable>()

    /** Log id -> error thrown by [markRepost] before anything is written. */
    val repostErrors = mutableMapOf<String, Throwable>()

    /** Bank side log id -> transaction id, for each successful [book]. */
    val booked = mutableMapOf<String, String>()

    /** Log id -> link passed to [markRepost]. */
    val reposted = mutableMapOf<String, String?>()

    /** Log id -> reason passed to [fail]. */
    val failed = mutableMapOf<String, String>()

    /** Ids of the logs passed to [ignore]. */
    val ignored = mutableListOf<String>()

    /** The `now` of each successful [book]. */
    val bookTimes = mutableListOf<Long>()

    override suspend fun getPendingLogs(): List<NotificationLog> =
        logs.filter { it.status == NotificationStatus.PENDING }.sortedWith(compareBy({ it.postTime }, { it.id }))

    override suspend fun getSettledLogsSince(since: Long): List<NotificationLog> =
        logs
            .filter {
                it.status != NotificationStatus.PENDING &&
                    it.status != NotificationStatus.IGNORED_SPAM &&
                    it.postTime >= since
            }.sortedWith(compareBy({ it.postTime }, { it.id }))

    override suspend fun getAccounts(): List<Account> = accounts

    override suspend fun getIdentifiers(): List<AccountIdentifier> = identifiers

    override suspend fun getRulesById(): Map<String, ParserRule> = rules

    override suspend fun book(
        decision: SettlementDecision.Book,
        transactionId: String,
        now: Long,
    ): SettlementError? {
        bookErrors[decision.bankSide.id]?.let { throw it }
        bookRejections[decision.bankSide.id]?.let { return it }
        bookTimes += now
        booked[decision.bankSide.id] = transactionId
        update(decision.bankSide.id, NotificationStatus.CONFIRMED, transactionId, null)
        decision.walletSide?.let { update(it.id, NotificationStatus.PAIRED_TRANSFER, transactionId, null) }
        return null
    }

    override suspend fun fail(decision: SettlementDecision.Fail): Boolean {
        failErrors[decision.log.id]?.let { throw it }
        failed[decision.log.id] = decision.reason
        val changed = update(decision.log.id, NotificationStatus.FAILED, null, decision.reason)
        val partnerChanged =
            decision.partner?.let { update(it.id, NotificationStatus.FAILED, null, FAIL_PARTNER_FAILED) }
        return changed || partnerChanged == true
    }

    override suspend fun ignore(decision: SettlementDecision.Ignore): Boolean {
        ignoreErrors[decision.log.id]?.let { throw it }
        val ids = listOfNotNull(decision.log.id, decision.partner?.id)
        ignored += ids
        return ids.map { update(it, NotificationStatus.IGNORED_ACCOUNT, null, null) }.any { it }
    }

    override suspend fun markRepost(
        logId: String,
        linkedTransactionId: String?,
    ): Boolean {
        repostErrors[logId]?.let { throw it }
        reposted[logId] = linkedTransactionId
        return update(logId, NotificationStatus.DEDUPLICATED, linkedTransactionId, null)
    }

    private fun update(
        id: String,
        status: NotificationStatus,
        link: String?,
        reason: String?,
    ): Boolean {
        val index = logs.indexOfFirst { it.id == id && it.status == NotificationStatus.PENDING }
        if (index < 0) return false
        logs[index] = logs[index].copy(status = status, linkedTransactionId = link, failureReason = reason)
        return true
    }
}
