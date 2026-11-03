package io.github.toanhp123.finote.data.account

import androidx.room3.withWriteTransaction
import io.github.toanhp123.finote.data.FinoteDatabase
import io.github.toanhp123.finote.domain.account.model.Account
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
        private val transactions = db.transactionDao()
        private val rebuilder = AccountBalanceRebuilder(accounts, transactions, db.accountBalanceSnapshotDao(), clock)

        override suspend fun getAccount(id: String): Account? = accounts.getById(id)?.toDomain()

        override fun observeAccounts(): Flow<List<Account>> =
            accounts.observeAccounts().map { list -> list.map { it.toDomain() } }

        override suspend fun add(account: Account) {
            accounts.insert(account.toEntity().copy(currentBalance = account.initialBalance))
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

        private suspend fun requireLive(id: String): AccountEntity {
            val old = requireNotNull(accounts.getById(id)) { "account does not exist" }
            require(old.deletedAt == null) { "account is deleted" }
            return old
        }
    }
