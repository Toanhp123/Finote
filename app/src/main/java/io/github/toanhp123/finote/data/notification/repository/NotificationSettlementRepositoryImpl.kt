package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.mapper.toEntity
import io.github.toanhp123.finote.data.category.mapper.toDomain
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.repository.LedgerWriter
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.service.isCoveredByAdjustment
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.RuleMatch
import io.github.toanhp123.finote.domain.category.service.classify
import io.github.toanhp123.finote.domain.notification.error.SettlementError
import io.github.toanhp123.finote.domain.notification.model.AccountResolution
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.notification.service.FAIL_PARTNER_FAILED
import io.github.toanhp123.finote.domain.notification.service.resolveAccount
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.service.TRANSFER_PAIR_WINDOW_MILLIS
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import io.github.toanhp123.finote.domain.transaction.service.buildPairedTransfer
import io.github.toanhp123.finote.domain.transaction.service.findTransferPartner
import io.github.toanhp123.finote.domain.transaction.service.pairingTime
import java.time.Clock
import java.time.Instant
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
        private val identifiers = db.accountIdentifierDao()
        private val pairs = db.transferPairDao()
        private val ledger = LedgerWriter(db, clock)

        override suspend fun getPendingLogs(): List<NotificationLog> =
            storage { logs.getPending().map { it.toDomain() } }

        override suspend fun getSettledLogsSince(since: Long): List<NotificationLog> =
            storage { logs.getSettledSince(since).map { it.toDomain() } }

        override suspend fun getAccounts(): List<Account> = storage { accounts.getWithBankKey().map { it.toDomain() } }

        override suspend fun getIdentifiers(): List<AccountIdentifier> =
            storage { identifiers.getAll().map { it.toDomain() } }

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
                    accountFor(bank.packageName, parsed.accountMask, decision.rule, now)
                        ?: return@storageWrite SettlementError.AccountUnusable
                // An adjustment already includes this late log; booking it would count it twice.
                val rows = db.transactionDao().getActiveByAccount(account.id).map { it.toDomain() }
                if (isCoveredByAdjustment(
                        account.id,
                        bank.postTime,
                        rows,
                    )
                ) {
                    return@storageWrite SettlementError.AdjustedAfter
                }
                val (match, categoryType) = categorize(decision.merchant, decision.note, parsed.type)
                val transaction =
                    Transaction(
                        id = transactionId,
                        amount = parsed.amount,
                        feeAmount = 0L,
                        type = parsed.type,
                        adjustmentDirection = null,
                        accountId = account.id,
                        toAccountId = null,
                        timestamp = bank.postTime,
                        deletedAt = null,
                        createdAt = now,
                        updatedAt = now,
                        categoryId = match?.categoryId,
                        merchantName = match?.merchantName ?: decision.merchant,
                        note = decision.note.orEmpty(),
                        source = TransactionSource.NOTIFICATION_PARSER,
                        postBalance = parsed.postBalance,
                        notificationLogId = bank.id,
                    )
                TransactionRules
                    .check(transaction, Instant.ofEpochMilli(now), setOf(account.id), categoryType)
                    ?.let { return@storageWrite SettlementError.Refused(it) }
                val pair = findPair(transaction, account, now)
                if (pair == null) {
                    ledger.insert(transaction, coldStart = true)
                } else {
                    ledger.insertPaired(pair.transfer, transaction, pair.partner, now)
                }
                val status = if (pair == null) NotificationStatus.CONFIRMED else NotificationStatus.PAIRED_TRANSFER
                val error =
                    settle(listOf(bank.id), status, transactionId)
                        ?: decision.walletSide?.let {
                            settle(
                                listOf(it.id),
                                NotificationStatus.DEDUPLICATED,
                                transactionId,
                            )
                        }
                // The partner's own log is already CONFIRMED; a missing one (deleted, link cleared) needs nothing.
                if (error == null) pair?.partner?.notificationLogId?.let { logs.markPairedTransfer(it) }
                error
            }

        /** The first keyword rule that matches, with the type of its category (for [TransactionRules.check]). */
        private suspend fun categorize(
            merchant: String?,
            note: String?,
            type: TransactionType,
        ): Pair<RuleMatch?, CategoryType?> {
            val categories = db.categoryDao().getLive().associate { it.id to it.toDomain() }
            val rules = db.keywordRuleDao().getAll().map { it.toDomain() }
            val match = classify(merchant, note, type, rules, categories)
            return match to match?.let { categories[it.categoryId]?.type }
        }

        private class PairedBooking(
            val partner: Transaction,
            val transfer: Transaction,
        )

        /** The stored row [row] pairs with and the transfer that replaces both; null books [row] alone. */
        private suspend fun findPair(
            row: Transaction,
            account: AccountEntity,
            now: Long,
        ): PairedBooking? {
            val window = TRANSFER_PAIR_WINDOW_MILLIS
            val candidates =
                pairs
                    .getPairCandidates(row.amount, pairingTime(row) - window, pairingTime(row) + window)
                    .map { it.toDomain() }
            val usable = HashMap<String, Account>()
            usable[account.id] = account.toDomain()
            candidates.map { it.accountId }.distinct().forEach { id ->
                accounts
                    .getById(id)
                    ?.toDomain()
                    ?.takeIf { it.isUsable }
                    ?.let { usable[id] = it }
            }
            val partner = findTransferPartner(row, candidates, usable) ?: return null
            val transfer = buildPairedTransfer(row, partner, UUID.randomUUID().toString(), now)
            val refused =
                TransactionRules.check(
                    transfer,
                    Instant.ofEpochMilli(now),
                    setOf(row.accountId, partner.accountId),
                    categoryType = null,
                )
            return if (refused == null) PairedBooking(partner, transfer) else null
        }

        /**
         * The bank side gets the decision's reason, the wallet partner [FAIL_PARTNER_FAILED]: only the bank side is an
         * inbox item. One write, so a crash cannot leave the wallet `PENDING` to be booked alone.
         */
        override suspend fun fail(decision: SettlementDecision.Fail): Boolean =
            db.storageWrite {
                val bank = markFailed(decision.log.id, decision.reason)
                val wallet = decision.partner?.let { markFailed(it.id, FAIL_PARTNER_FAILED) } ?: 0
                bank + wallet > 0
            }

        private suspend fun markFailed(
            id: String,
            reason: String,
        ): Int = logs.markSettled(listOf(id), NotificationStatus.FAILED, null, reason)

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

        /**
         * Resolves the account again on the rows this write transaction sees: the plan of one settle run goes stale
         * as earlier decisions book. Null when the account is ignored or rejected now.
         */
        private suspend fun accountFor(
            packageName: String,
            mask: String?,
            rule: ParserRule?,
            now: Long,
        ): AccountEntity? {
            val resolution =
                resolveAccount(
                    packageName,
                    mask,
                    rule,
                    accounts.getWithBankKey().map { it.toDomain() },
                    identifiers.getByPackage(packageName).map { it.toDomain() },
                )
            val target = (resolution as? AccountResolution.Resolved)?.target ?: return null
            val (account, key) =
                when (target) {
                    is AccountTarget.Existing -> {
                        checkNotNull(accounts.getById(target.accountId)) to target.attach
                    }

                    is AccountTarget.New -> {
                        val created = newAccount(target, now)
                        created to target.mask?.let { AccountIdentifier(packageName, target.kind, it, created.id) }
                    }
                }
            key?.let { attach(it) }
            return account
        }

        /** Stores [key] unless a live account holds it; a key whose owner is deleted is free and moves over. */
        private suspend fun attach(key: AccountIdentifier) {
            val current = identifiers.get(key.packageName, key.kind, key.mask)
            if (current == null) {
                identifiers.insert(key.toEntity())
            } else if (accounts.getById(current.accountId)?.deletedAt != null) {
                identifiers.repoint(key.packageName, key.kind, key.mask, key.accountId)
            }
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
                    sortOrder = (accounts.getMaxSortOrder() ?: -1) + 1,
                    isExcludedFromNetWorth = false,
                    isArchived = false,
                    isIgnored = false,
                    isInitialized = false,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                    bankPackageName = target.packageName,
                    mergedIntoId = null,
                )
            accounts.insert(account)
            return account
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
