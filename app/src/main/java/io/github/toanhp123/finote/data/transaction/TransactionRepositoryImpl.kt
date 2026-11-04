package io.github.toanhp123.finote.data.transaction

import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.data.account.AccountBalanceRebuilder
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
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

        override suspend fun getTransaction(id: String): Transaction? = transactions.getById(id)?.toDomain()

        override suspend fun getActiveAccountIds(accountIds: Set<String>): Set<String> =
            accountIds.filterTo(mutableSetOf()) { id ->
                db.accountDao().getById(id)?.let { it.deletedAt == null && !it.isArchived } == true
            }

        override suspend fun getCategoryType(categoryId: String): CategoryType? =
            db.categoryDao().getById(categoryId)?.type

        override suspend fun getLastManualAccountId(): String? = transactions.getLastManualAccountId()

        override suspend fun getLastManualCategoryId(type: TransactionType): String? =
            transactions.getLastManualCategoryId(type)

        override suspend fun add(transaction: Transaction) {
            db.withWriteTransaction {
                val entity = transaction.toEntity()
                requireActiveAccounts(entity.accountIds())
                transactions.insert(entity)
                rebuilder.rebuild(entity.accountIds())
            }
        }

        override suspend fun update(transaction: Transaction) {
            db.withWriteTransaction {
                val old = requireNotNull(transactions.getById(transaction.id)) { "transaction does not exist" }
                require(old.deletedAt == null) { "transaction is deleted" }
                val entity = transaction.toEntity()
                requireActiveAccounts(old.accountIds() + entity.accountIds())
                transactions.update(entity)
                rebuilder.rebuild(old.accountIds() + entity.accountIds())
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.withWriteTransaction {
                val old = requireNotNull(transactions.getById(id)) { "transaction does not exist" }
                if (old.deletedAt != null) return@withWriteTransaction
                transactions.update(old.copy(deletedAt = deletedAt, updatedAt = deletedAt))
                rebuilder.rebuild(old.accountIds())
            }
        }

        /** Re-checks inside the write transaction: the use case's early check can be stale by now. */
        private suspend fun requireActiveAccounts(wanted: Set<String>) {
            require(
                getActiveAccountIds(wanted).containsAll(wanted),
            ) { "account does not exist, is deleted or archived" }
        }

        private fun TransactionEntity.accountIds() = setOfNotNull(accountId, toAccountId)
    }
