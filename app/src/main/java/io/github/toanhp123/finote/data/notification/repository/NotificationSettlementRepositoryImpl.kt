package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.repository.AccountBalanceRebuilder
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.service.BalanceCalculator
import io.github.toanhp123.finote.domain.account.service.anchoredInitialBalance
import io.github.toanhp123.finote.domain.notification.error.SettlementError
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

        override suspend fun getPendingLogs(): List<NotificationLog> =
            storage { logs.getPending().map { it.toDomain() } }

        override suspend fun getSettledLogsSince(since: Long): List<NotificationLog> =
            storage { logs.getSettledSince(since).map { it.toDomain() } }

        override suspend fun getAccounts(): List<Account> = storage { accounts.getWithBankKey().map { it.toDomain() } }

        override suspend fun getRulesById(): Map<String, ParserRule> =
            storage {
                db
                    .parserRuleDao()
                    .getAll()
                    .mapNotNull { it.toDomain() }
                    .associateBy { it.id }
            }

        override suspend fun book(
            decision: SettlementDecision.Book,
            transactionId: String,
            now: Long,
        ): SettlementError? =
            db.storageWrite {
                val bank = decision.bankSide
                val parsed = checkNotNull(bank.parsed) { "bank side has no parsed fields" }
                val account =
                    resolveAccount(decision.account, now) ?: return@storageWrite SettlementError.AccountUnusable
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
                coldStart(account)
                rebuilder.rebuild(setOf(account.id))
                settle(listOf(bank.id), NotificationStatus.CONFIRMED, transactionId)
                    ?: decision.walletSide?.let {
                        settle(
                            listOf(it.id),
                            NotificationStatus.DEDUPLICATED,
                            transactionId,
                        )
                    }
            }

        override suspend fun fail(decision: SettlementDecision.Fail): Boolean =
            storage {
                logs.markSettled(
                    ids = listOfNotNull(decision.log.id, decision.partner?.id),
                    status = NotificationStatus.FAILED,
                    linkedTransactionId = null,
                    failureReason = decision.reason,
                ) > 0
            }

        override suspend fun ignore(decision: SettlementDecision.Ignore): Boolean =
            storage {
                logs.markSettled(
                    ids = listOfNotNull(decision.log.id, decision.partner?.id),
                    status = NotificationStatus.IGNORED_ACCOUNT,
                    linkedTransactionId = null,
                    failureReason = null,
                ) > 0
            }

        override suspend fun markRepost(
            logId: String,
            linkedTransactionId: String?,
        ): Boolean =
            storage { logs.markSettled(listOf(logId), NotificationStatus.DEDUPLICATED, linkedTransactionId, null) == 1 }

        private suspend fun resolveAccount(
            target: AccountTarget,
            now: Long,
        ): AccountEntity? {
            val account =
                when (target) {
                    is AccountTarget.Existing -> {
                        accounts.getById(target.accountId)
                    }

                    is AccountTarget.New -> {
                        accounts.getByBankKey(target.packageName, target.mask)
                            ?: newAccount(target, now)
                    }
                }
            // The planner's check can be stale by now.
            return account?.takeIf { it.toDomain().isUsable }
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

        /** The balance anchor of an uninitialized account fixes its start. A credit card has no anchor. */
        private suspend fun coldStart(account: AccountEntity) {
            if (account.isInitialized) return
            val rows = transactions.getActiveByAccount(account.id).map { it.toDomain() }
            BalanceCalculator.anchoredInitialBalance(account.toDomain(), rows)?.let {
                accounts.update(account.copy(initialBalance = it, isInitialized = true))
            }
        }

        private suspend fun settle(
            ids: List<String>,
            status: NotificationStatus,
            transactionId: String,
        ): SettlementError? =
            if (logs.markSettled(ids, status, transactionId, null) == ids.size) null else SettlementError.LogNotPending

        private companion object {
            const val ACCOUNT_COLOR = 0xFF546E7A.toInt()
        }
    }
