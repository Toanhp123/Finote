package io.github.toanhp123.finote.data.account.repository

import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.mapper.toEntity
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
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
        private val identifiers = db.accountIdentifierDao()
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
                accounts.update(
                    old.copy(isArchived = archived, isPrimary = old.isPrimary && !archived, updatedAt = updatedAt),
                )
            }
        }

        override suspend fun softDelete(
            id: String,
            deletedAt: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                if (old.deletedAt != null) return@storageWrite
                accounts.update(old.copy(deletedAt = deletedAt, isPrimary = false, updatedAt = deletedAt))
            }
        }

        override suspend fun getLiveTransactionCount(id: String): Int =
            storage { transactions.countActiveByAccount(id) }

        override suspend fun setIgnored(
            id: String,
            ignored: Boolean,
            shouldTrashOldTransactions: Boolean,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                accounts.update(old.copy(isIgnored = ignored, isPrimary = old.isPrimary && !ignored, updatedAt = now))
                if (ignored && shouldTrashOldTransactions) {
                    transactions.trashNonTransfers(id, now)
                    rebuilder.rebuild(setOf(id))
                }
            }
        }

        override suspend fun getTrashableTransactionCount(id: String): Int =
            storage { transactions.countNonTransfers(id) }

        override suspend fun getNextSortOrder(): Int = storage { (accounts.getMaxSortOrder() ?: -1) + 1 }

        override suspend fun setInitialBalance(
            id: String,
            initialBalance: Long,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                accounts.update(old.copy(initialBalance = initialBalance, isInitialized = true, updatedAt = now))
                rebuilder.rebuild(setOf(id))
            }
        }

        override suspend fun setPrimary(
            id: String,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                accounts.clearPrimaryExcept(id, now)
                accounts.update(old.copy(isPrimary = true, updatedAt = now))
            }
        }

        override suspend fun clearPrimary(
            id: String,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                if (old.isPrimary) accounts.update(old.copy(isPrimary = false, updatedAt = now))
            }
        }

        override suspend fun reorder(
            orderedIds: List<String>,
            now: Long,
        ) {
            db.storageWrite { orderedIds.forEachIndexed { index, id -> accounts.setSortOrder(id, index, now) } }
        }

        override suspend fun setBankPackage(
            id: String,
            packageName: String,
            now: Long,
        ) {
            db.storageWrite {
                val old = accounts.getById(id) ?: return@storageWrite
                if (old.bankPackageName != null) return@storageWrite
                accounts.update(old.copy(bankPackageName = packageName, updatedAt = now))
            }
        }

        override fun observeIdentifiers(accountId: String): Flow<List<AccountIdentifier>> =
            identifiers.observeByAccount(accountId).map { list -> list.map { it.toDomain() } }.mapStorageErrors()

        override fun observeIdentifiedAccountIds(): Flow<Set<String>> =
            identifiers
                .observeOwnerIds()
                .map { it.toSet() }
                .distinctUntilChanged()
                .mapStorageErrors()

        override suspend fun getIdentifier(
            packageName: String,
            kind: IdentifierKind,
            mask: String,
        ): AccountIdentifier? = storage { identifiers.get(packageName, kind, mask)?.toDomain() }

        override suspend fun addIdentifier(identifier: AccountIdentifier) {
            db.storageWrite {
                val current = identifiers.get(identifier.packageName, identifier.kind, identifier.mask)
                if (current != null && accounts.getById(current.accountId)?.deletedAt != null) {
                    identifiers.repoint(identifier.packageName, identifier.kind, identifier.mask, identifier.accountId)
                } else {
                    identifiers.insert(identifier.toEntity())
                }
            }
        }

        override suspend fun removeIdentifier(identifier: AccountIdentifier) {
            storage { identifiers.delete(identifier.packageName, identifier.kind, identifier.mask) }
        }
    }
