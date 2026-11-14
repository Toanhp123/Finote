package io.github.toanhp123.finote.data.account.repository

import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.BalanceCalculator
import io.github.toanhp123.finote.domain.account.service.anchoredInitialBalance
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

        override suspend fun getAccount(id: String): Account? = storage { accounts.getById(id)?.toDomain() }

        override fun observeAccounts(): Flow<List<Account>> =
            accounts.observeAccounts().map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override suspend fun add(account: Account) {
            storage {
                accounts.insert(account.toEntity().copy(currentBalance = account.initialBalance, nameKey = null))
            }
        }

        override suspend fun update(account: Account) {
            db.storageWrite {
                val old = accounts.getById(account.id) ?: return@storageWrite
                accounts.update(
                    old.copy(
                        name = account.name,
                        nameKey = if (account.name == old.name) old.nameKey else null,
                        icon = account.icon,
                        color = account.color,
                        sortOrder = account.sortOrder,
                        isExcludedFromNetWorth = account.isExcludedFromNetWorth,
                        updatedAt = account.updatedAt,
                    ),
                )
            }
        }

        override suspend fun setArchived(
            id: String,
            archived: Boolean,
            updatedAt: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                accounts.update(old.copy(isArchived = archived, updatedAt = updatedAt))
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                if (old.deletedAt != null) return@storageWrite
                accounts.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
            }
        }

        override suspend fun merge(
            sourceId: String,
            targetId: String,
            now: Long,
        ) {
            db.storageWrite {
                val source = accounts.getById(sourceId) ?: return@storageWrite
                val target = accounts.getById(targetId) ?: return@storageWrite
                merges.moveSource(sourceId, targetId, now)
                merges.moveDestination(sourceId, targetId, now)
                accounts.update(withMergedStart(source, target).copy(updatedAt = now))
                merges.repointMerged(sourceId, targetId)
                accounts.update(source.copy(deletedAt = now, mergedIntoId = targetId, updatedAt = now))
                db.accountBalanceSnapshotDao().deleteByAccount(sourceId)
                rebuilder.rebuild(setOf(targetId))
            }
        }

        override suspend fun getLiveTransactionCount(id: String): Int =
            storage { transactions.countActiveByAccount(id) }

        override suspend fun countTransfersBetween(
            sourceId: String,
            targetId: String,
        ): Int = storage { merges.countTransfersBetween(sourceId, targetId) }

        override suspend fun setIgnored(
            id: String,
            ignored: Boolean,
            shouldTrashOldTransactions: Boolean,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                accounts.update(old.copy(isIgnored = ignored, updatedAt = now))
                if (ignored && shouldTrashOldTransactions) {
                    transactions.trashNonTransfers(id, now)
                    rebuilder.rebuild(setOf(id))
                }
            }
        }

        override suspend fun getTrashableTransactionCount(id: String): Int =
            storage { transactions.countNonTransfers(id) }

        /**
         * Both rows are one real bank account, so its balance already includes the source's transactions. Re-anchor the
         * target start on the latest bank balance over the merged set, else keep the old start (or the source's).
         * Runs after the move, so the target's live rows are the merged set.
         */
        private suspend fun withMergedStart(
            source: AccountEntity,
            target: AccountEntity,
        ): AccountEntity {
            val rows = transactions.getActiveByAccount(target.id).map { it.toDomain() }
            val anchored = BalanceCalculator.anchoredInitialBalance(target.toDomain(), rows)
            return when {
                anchored != null -> {
                    target.copy(initialBalance = anchored, isInitialized = true)
                }

                source.isInitialized && !target.isInitialized -> {
                    target.copy(initialBalance = source.initialBalance, isInitialized = true)
                }

                else -> {
                    target
                }
            }
        }
    }
