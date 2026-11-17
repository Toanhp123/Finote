package io.github.toanhp123.finote.data.transaction.repository

import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

class TransferPairRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
    ) : TransferPairRepository {
        private val transactions = db.transactionDao()
        private val pairs = db.transferPairDao()
        private val ledger = LedgerWriter(db, clock)

        override suspend fun getPairedOriginals(transferId: String): List<Transaction> =
            storage { pairs.getPairedOriginals(transferId).map { it.toDomain() } }

        override fun observeAutoPairedIds(): Flow<Set<String>> =
            pairs.observeAutoPairedIds().map { it.toSet() }.mapStorageErrors()

        override suspend fun unpair(
            transferId: String,
            now: Long,
        ) {
            db.storageWrite {
                val transfer = checkNotNull(transactions.getById(transferId))
                val originals = pairs.getPairedOriginals(transferId)
                transactions.update(transfer.copy(deletedAt = now, updatedAt = now))
                originals.forEach {
                    // One past createdAt: pair candidates need updatedAt = createdAt, so these never pair again.
                    transactions.update(
                        it.copy(deletedAt = null, pairedIntoId = null, updatedAt = maxOf(now, it.createdAt + 1)),
                    )
                }
                db.notificationLogDao().markUnpaired(originals.mapNotNull { it.notificationLogId })
                ledger.rebuild(transfer.accountIds() + originals.flatMap { it.accountIds() })
            }
        }

        private fun TransactionEntity.accountIds() = setOfNotNull(accountId, toAccountId)
    }
