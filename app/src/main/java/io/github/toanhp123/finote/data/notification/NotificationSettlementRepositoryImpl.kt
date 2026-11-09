package io.github.toanhp123.finote.data.notification

import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.account.AccountBalanceRebuilder
import io.github.toanhp123.finote.data.account.AccountEntity
import io.github.toanhp123.finote.data.account.toDomain
import io.github.toanhp123.finote.data.transaction.TransactionEntity
import io.github.toanhp123.finote.data.transaction.toDomain
import io.github.toanhp123.finote.domain.account.BalanceCalculator
import io.github.toanhp123.finote.domain.account.initialBalanceFor
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class NotificationSettlementRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
    ) : NotificationSettlementRepository {
        private val logs = db.notificationLogDao()
        private val accounts = db.accountDao()
        private val transactions = db.transactionDao()
        private val rebuilder = AccountBalanceRebuilder(accounts, transactions, db.accountBalanceSnapshotDao(), clock)

        override suspend fun getPendingLogs(): List<NotificationLog> = logs.getPending().map { it.toDomain() }

        override suspend fun getSettledLogsSince(since: Long): List<NotificationLog> =
            logs.getSettledSince(since).map { it.toDomain() }

        override suspend fun getAccounts(): List<Account> = accounts.getWithBankKey().map { it.toDomain() }

        override suspend fun getRulesById(): Map<String, ParserRule> =
            db
                .parserRuleDao()
                .getAll()
                .mapNotNull { it.toDomain() }
                .associateBy { it.id }

        override suspend fun book(
            decision: SettlementDecision.Book,
            transactionId: String,
            now: Long,
        ) {
            db.withWriteTransaction {
                val bank = decision.bankSide
                val parsed = checkNotNull(bank.parsed) { "bank side has no parsed fields" }
                val account = resolveAccount(decision.account, now)
                transactions.insert(
                    TransactionEntity(
                        id = transactionId,
                        amount = parsed.amount,
                        feeAmount = 0L,
                        type = parsed.type,
                        adjustmentDirection = null,
                        accountId = account.id,
                        toAccountId = null,
                        categoryId = null,
                        timestamp = bank.postTime,
                        merchantName = decision.merchant,
                        note = decision.note.orEmpty(),
                        source = TransactionSource.NOTIFICATION_PARSER,
                        isExcludedFromAnalytics = false,
                        reimbursementStatus = ReimbursementStatus.NONE,
                        createdAt = now,
                        updatedAt = now,
                        deletedAt = null,
                        postBalance = parsed.postBalance,
                        notificationLogId = bank.id,
                    ),
                )
                coldStart(account, parsed.postBalance, bank.postTime)
                rebuilder.rebuild(setOf(account.id))
                settle(listOf(bank.id), NotificationStatus.CONFIRMED, transactionId)
                decision.walletSide?.let { settle(listOf(it.id), NotificationStatus.DEDUPLICATED, transactionId) }
            }
        }

        override suspend fun fail(decision: SettlementDecision.Fail): Boolean =
            logs.markSettled(
                ids = listOfNotNull(decision.log.id, decision.partner?.id),
                status = NotificationStatus.FAILED,
                linkedTransactionId = null,
                failureReason = decision.reason,
            ) > 0

        override suspend fun ignore(decision: SettlementDecision.Ignore): Boolean =
            logs.markSettled(
                ids = listOfNotNull(decision.log.id, decision.partner?.id),
                status = NotificationStatus.IGNORED_ACCOUNT,
                linkedTransactionId = null,
                failureReason = null,
            ) > 0

        override suspend fun markRepost(
            logId: String,
            linkedTransactionId: String?,
        ): Boolean = logs.markSettled(listOf(logId), NotificationStatus.DEDUPLICATED, linkedTransactionId, null) == 1

        private suspend fun resolveAccount(
            target: AccountTarget,
            now: Long,
        ): AccountEntity {
            val account =
                when (target) {
                    is AccountTarget.Existing -> {
                        checkNotNull(accounts.getById(target.accountId)) { "account is gone" }
                    }

                    is AccountTarget.New -> {
                        accounts.getByBankKey(target.packageName, target.mask) ?: newAccount(target, now)
                    }
                }
            // The planner's check can be stale by now; throwing rolls back and leaves the log PENDING.
            check(account.deletedAt == null && !account.isArchived && !account.isIgnored) {
                "account is deleted, archived or ignored"
            }
            return account
        }

        private suspend fun newAccount(
            target: AccountTarget.New,
            now: Long,
        ): AccountEntity {
            val account =
                AccountEntity(
                    id = UUID.randomUUID().toString(),
                    name = target.mask?.let { "${target.bankName} ••$it" } ?: target.bankName,
                    nameKey = null,
                    type = target.type,
                    initialBalance = 0L,
                    currentBalance = 0L,
                    currency = "VND",
                    icon = target.type.name.lowercase(),
                    color = ACCOUNT_COLOR,
                    sortOrder = 0,
                    isExcludedFromNetWorth = false,
                    isArchived = false,
                    isIgnored = false,
                    isInitialized = false,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    bankPackageName = target.packageName,
                    accountNumberMask = target.mask,
                    mergedIntoId = null,
                )
            accounts.insert(account)
            return account
        }

        /** The first bank balance of an uninitialized account fixes its start. A credit card has no such balance. */
        private suspend fun coldStart(
            account: AccountEntity,
            postBalance: Long?,
            postTime: Long,
        ) {
            if (account.isInitialized || account.type == AccountType.CREDIT_CARD || postBalance == null) return
            val upToNow =
                transactions
                    .getActiveByAccount(account.id)
                    .filter { it.timestamp <= postTime }
                    .map { it.toDomain() }
            val initial = BalanceCalculator.initialBalanceFor(account.id, postBalance, upToNow)
            accounts.update(account.copy(initialBalance = initial, isInitialized = true))
        }

        private suspend fun settle(
            ids: List<String>,
            status: NotificationStatus,
            transactionId: String,
        ) {
            val updated = logs.markSettled(ids, status, transactionId, null)
            check(updated == ids.size) { "log is no longer pending" }
        }

        private companion object {
            const val ACCOUNT_COLOR = 0xFF546E7A.toInt()
        }
    }
