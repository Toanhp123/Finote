package io.github.toanhp123.finote.data.account.repository

import io.github.toanhp123.finote.data.account.local.entity.AccountEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository
import io.github.toanhp123.finote.domain.account.service.BalanceCalculator
import io.github.toanhp123.finote.domain.account.service.anchoredInitialBalance
import io.github.toanhp123.finote.domain.account.service.isStartLocked
import io.github.toanhp123.finote.domain.account.service.provisionalStart
import java.time.Clock
import javax.inject.Inject

class AccountMergeRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
    ) : AccountMergeRepository {
        private val accounts = db.accountDao()
        private val merges = db.accountMergeDao()
        private val transactions = db.transactionDao()
        private val rebuilder = AccountBalanceRebuilder(accounts, transactions, db.accountBalanceSnapshotDao(), clock)

        override suspend fun merge(
            sourceId: String,
            targetId: String,
            now: Long,
        ) {
            db.storageWrite {
                val source = accounts.getById(sourceId) ?: return@storageWrite
                val target = accounts.getById(targetId) ?: return@storageWrite
                merges.moveSource(sourceId, targetId)
                merges.moveDestination(sourceId, targetId)
                db.accountIdentifierDao().moveAll(sourceId, targetId)
                accounts.update(withMergedStart(source, target).copy(updatedAt = now))
                merges.repointMerged(sourceId, targetId)
                accounts.update(source.copy(deletedAt = now, mergedIntoId = targetId, updatedAt = now))
                db.accountBalanceSnapshotDao().deleteByAccount(sourceId)
                rebuilder.rebuild(setOf(targetId))
            }
        }

        override suspend fun getMergeDroppedStart(
            sourceId: String,
            targetId: String,
        ): Long? =
            storage {
                val source = accounts.getById(sourceId)?.takeIf { it.isInitialized }
                val target = accounts.getById(targetId)?.takeIf { it.isInitialized }
                if (source == null || target == null) {
                    null
                } else {
                    // The merge has not run: re-home the source's rows to the target the way the move will.
                    val merged =
                        (transactions.getActiveByAccount(sourceId) + transactions.getActiveByAccount(targetId))
                            .distinctBy { it.id }
                            .map { it.toDomain() }
                            .map { row ->
                                row.copy(
                                    accountId = if (row.accountId == sourceId) targetId else row.accountId,
                                    toAccountId = if (row.toAccountId == sourceId) targetId else row.toAccountId,
                                )
                            }
                    val anchored = BalanceCalculator.anchoredInitialBalance(target.toDomain(), merged)
                    // Dropping a zero start loses nothing, so it gets no warning.
                    source.initialBalance.takeIf { anchored == null && it != 0L }
                }
            }

        override suspend fun countTransfersBetween(
            sourceId: String,
            targetId: String,
        ): Int = storage { merges.countTransfersBetween(sourceId, targetId) }

        /**
         * Both rows are one real bank account, so its balance already includes the source's transactions. A provisional
         * target takes the young-account start of the merged set (and locks when its balance rows span the order
         * window); an initialized one re-anchors on the latest bank balance. With neither, keep the old start (or the
         * source's). Runs after the move, so the target's live rows are the merged set.
         */
        private suspend fun withMergedStart(
            source: AccountEntity,
            target: AccountEntity,
        ): AccountEntity {
            val rows = transactions.getActiveByAccount(target.id).map { it.toDomain() }
            val start =
                if (target.isInitialized) {
                    BalanceCalculator.anchoredInitialBalance(target.toDomain(), rows)
                } else {
                    provisionalStart(target.toDomain(), rows)
                }
            return when {
                start != null -> {
                    target.copy(
                        initialBalance = start,
                        isInitialized = target.isInitialized || isStartLocked(target.id, rows),
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
    }
