package io.github.toanhp123.finote.data.transaction.repository

import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.repository.AccountBalanceRebuilder
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.mapper.toEntity
import io.github.toanhp123.finote.domain.account.service.detectBalanceDrift
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class TransactionRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
    ) : TransactionRepository {
        private val transactions = db.transactionDao()
        private val rebuilder =
            AccountBalanceRebuilder(db.accountDao(), transactions, db.accountBalanceSnapshotDao(), clock)

        override suspend fun getTransaction(id: String): Transaction? = storage { transactions.getById(id)?.toDomain() }

        override suspend fun getUsableAccountIds(accountIds: Set<String>): Set<String> =
            storage {
                accountIds.filterTo(mutableSetOf()) { id ->
                    db
                        .accountDao()
                        .getById(id)
                        ?.toDomain()
                        ?.isUsable == true
                }
            }

        override suspend fun getCategoryType(categoryId: String): CategoryType? =
            storage { db.categoryDao().getById(categoryId)?.type }

        override suspend fun getLastManualAccountId(): String? = storage { transactions.getLastManualAccountId() }

        override suspend fun getLastManualCategoryId(type: TransactionType): String? =
            storage { transactions.getLastManualCategoryId(type) }

        override fun observeTransactions(accountId: String?): Flow<List<Transaction>> =
            transactions.observeTransactions(accountId).map { rows -> rows.map { it.toDomain() } }.mapStorageErrors()

        override suspend fun add(transaction: Transaction) {
            db.storageWrite {
                val entity = transaction.toEntity()
                transactions.insert(entity)
                rebuilder.rebuild(entity.accountIds())
            }
        }

        override suspend fun reconcile(
            id: String,
            accountId: String,
            actualBalance: Long,
            now: Long,
        ): Transaction? =
            db.storageWrite {
                val account = checkNotNull(db.accountDao().getById(accountId))
                writeAdjustment(id, accountId, Math.subtractExact(actualBalance, account.currentBalance), now)
            }

        override suspend fun adjustDrift(
            id: String,
            accountId: String,
            now: Long,
        ): Transaction? =
            db.storageWrite {
                val account = checkNotNull(db.accountDao().getById(accountId))
                val live = transactions.getActiveByAccount(accountId).map { it.toDomain() }
                val drift = detectBalanceDrift(account.toDomain(), live) ?: return@storageWrite null
                writeAdjustment(id, accountId, drift.difference, now)
            }

        override suspend fun update(transaction: Transaction) {
            db.storageWrite {
                val old = checkNotNull(transactions.getById(transaction.id))
                val entity = transaction.toEntity()
                transactions.update(entity)
                rebuilder.rebuild(old.accountIds() + entity.accountIds())
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.storageWrite {
                val old = checkNotNull(transactions.getById(id))
                if (old.deletedAt != null) return@storageWrite
                transactions.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
                rebuilder.rebuild(old.accountIds())
            }
        }

        /** Inserts one ADJUSTMENT worth [delta] (signed) and rebuilds the account; null and no write when 0. */
        private suspend fun writeAdjustment(
            id: String,
            accountId: String,
            delta: Long,
            now: Long,
        ): Transaction? {
            if (delta == 0L) return null
            val adjustment =
                Transaction(
                    id = id,
                    amount = if (delta > 0) delta else Math.negateExact(delta),
                    feeAmount = 0L,
                    type = TransactionType.ADJUSTMENT,
                    adjustmentDirection = if (delta > 0) AdjustmentDirection.IN else AdjustmentDirection.OUT,
                    accountId = accountId,
                    toAccountId = null,
                    timestamp = now,
                    deletedAt = null,
                    createdAt = now,
                    updatedAt = now,
                    source = TransactionSource.ADJUSTMENT,
                )
            transactions.insert(adjustment.toEntity())
            rebuilder.rebuild(setOf(accountId))
            return adjustment
        }

        private fun TransactionEntity.accountIds() = setOfNotNull(accountId, toAccountId)
    }
