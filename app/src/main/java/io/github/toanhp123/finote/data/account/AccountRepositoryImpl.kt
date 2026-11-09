package io.github.toanhp123.finote.data.account

import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.transaction.toDomain
import io.github.toanhp123.finote.domain.account.BalanceCalculator
import io.github.toanhp123.finote.domain.account.initialBalanceFor
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountMergeRejectedException
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.MergeRejection
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class AccountRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
    ) : AccountRepository {
        private val accounts = db.accountDao()
        private val merges = db.accountMergeDao()
        private val transactions = db.transactionDao()
        private val rebuilder = AccountBalanceRebuilder(accounts, transactions, db.accountBalanceSnapshotDao(), clock)

        override suspend fun getAccount(id: String): Account? = accounts.getById(id)?.toDomain()

        override fun observeAccounts(): Flow<List<Account>> =
            accounts.observeAccounts().map { list -> list.map { it.toDomain() } }

        override suspend fun add(account: Account) {
            accounts.insert(account.toEntity().copy(currentBalance = account.initialBalance, nameKey = null))
        }

        override suspend fun update(account: Account) {
            db.withWriteTransaction {
                val old = requireLive(account.id)
                accounts.update(
                    account.toEntity().copy(
                        currentBalance = old.currentBalance,
                        createdAt = old.createdAt,
                        deletedAt = old.deletedAt,
                        isArchived = old.isArchived,
                        isIgnored = old.isIgnored,
                        mergedIntoId = old.mergedIntoId,
                        nameKey = if (account.name == old.name) old.nameKey else null,
                    ),
                )
                if (account.initialBalance != old.initialBalance) rebuilder.rebuild(setOf(account.id))
            }
        }

        override suspend fun setArchived(
            id: String,
            archived: Boolean,
            updatedAt: Long,
        ) {
            db.withWriteTransaction {
                accounts.update(requireLive(id).copy(isArchived = archived, updatedAt = updatedAt))
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.withWriteTransaction {
                val old = requireNotNull(accounts.getById(id)) { "account does not exist" }
                if (old.deletedAt != null) return@withWriteTransaction
                require(transactions.countActiveByAccount(id) == 0) { "account still has transactions" }
                accounts.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
            }
        }

        override suspend fun merge(
            sourceId: String,
            targetId: String,
            now: Long,
        ) {
            db.withWriteTransaction {
                val (source, target) = requireMergeable(sourceId, targetId)
                merges.moveSource(sourceId, targetId, now)
                merges.moveDestination(sourceId, targetId, now)
                accounts.update(withMergedStart(source, target).copy(updatedAt = now))
                merges.repointMerged(sourceId, targetId)
                accounts.update(source.copy(deletedAt = now, mergedIntoId = targetId, updatedAt = now))
                db.accountBalanceSnapshotDao().deleteByAccount(sourceId)
                rebuilder.rebuild(setOf(targetId))
            }
        }

        override suspend fun getLiveTransactionCount(id: String): Int = transactions.countActiveByAccount(id)

        override suspend fun setIgnored(
            id: String,
            ignored: Boolean,
            shouldTrashOldTransactions: Boolean,
            now: Long,
        ) {
            db.withWriteTransaction {
                accounts.update(requireLive(id).copy(isIgnored = ignored, updatedAt = now))
                if (ignored && shouldTrashOldTransactions) {
                    transactions.trashNonTransfers(id, now)
                    rebuilder.rebuild(setOf(id))
                }
            }
        }

        override suspend fun getTrashableTransactionCount(id: String): Int = transactions.countNonTransfers(id)

        private suspend fun requireMergeable(
            sourceId: String,
            targetId: String,
        ): Pair<AccountEntity, AccountEntity> {
            fun reject(reason: MergeRejection): Nothing = throw AccountMergeRejectedException(reason)
            if (sourceId == targetId) reject(MergeRejection.SAME_ACCOUNT)
            val source = accounts.getById(sourceId)?.takeIf { it.deletedAt == null }
            val target = accounts.getById(targetId)?.takeIf { it.deletedAt == null }
            if (source == null || target == null) reject(MergeRejection.ACCOUNT_GONE)
            if (source.bankPackageName == null || source.bankPackageName != target.bankPackageName) {
                reject(MergeRejection.DIFFERENT_BANK)
            }
            if (merges.countTransfersBetween(sourceId, targetId) > 0) reject(MergeRejection.HAS_TRANSFER)
            return source to target
        }

        /**
         * Both rows are one real bank account, so its balance already includes the source's transactions. Re-anchor the
         * target start on the latest bank balance over the merged set, else keep the old start (or the source's).
         * Runs after the move, so the target's live rows are the merged set.
         */
        private suspend fun withMergedStart(
            source: AccountEntity,
            target: AccountEntity,
        ): AccountEntity {
            val rows = transactions.getActiveByAccount(target.id)
            val latest = rows.filter { it.postBalance != null }.maxWithOrNull(compareBy({ it.timestamp }, { it.id }))
            val postBalance = latest?.postBalance
            return when {
                latest != null && postBalance != null && target.type != AccountType.CREDIT_CARD -> {
                    val upToLatest = rows.filter { it.timestamp <= latest.timestamp }.map { it.toDomain() }
                    target.copy(
                        initialBalance = BalanceCalculator.initialBalanceFor(target.id, postBalance, upToLatest),
                        isInitialized = true,
                    )
                }

                source.isInitialized && !target.isInitialized -> {
                    target.copy(initialBalance = source.initialBalance, isInitialized = true)
                }

                else -> {
                    target
                }
            }
        }

        private suspend fun requireLive(id: String): AccountEntity {
            val old = requireNotNull(accounts.getById(id)) { "account does not exist" }
            require(old.deletedAt == null) { "account is deleted" }
            return old
        }
    }
