package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import java.time.Clock
import javax.inject.Inject

class UpdateTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Replaces a live transaction. Keeps `createdAt`, stamps `updatedAt`. A missing or deleted one is `NotFound`;
         * the accounts it leaves and the accounts it moves to must both be usable.
         */
        suspend operator fun invoke(transaction: Transaction): Outcome<Unit, TransactionError> =
            tx.write {
                val existing = repository.getTransaction(transaction.id)
                if (existing == null || existing.deletedAt != null) {
                    Outcome.Err(TransactionError.NotFound)
                } else {
                    val old = setOfNotNull(existing.accountId, existing.toAccountId)
                    if (repository.getUsableAccountIds(old).containsAll(old)) {
                        val stamp = clock.millis()
                        replace(transaction.copy(deletedAt = null, createdAt = existing.createdAt, updatedAt = stamp))
                    } else {
                        Outcome.Err(TransactionError.AccountUnusable)
                    }
                }
            }

        private suspend fun replace(updated: Transaction): Outcome<Unit, TransactionError> {
            val usable = repository.getUsableAccountIds(setOfNotNull(updated.accountId, updated.toAccountId))
            val categoryType = updated.categoryId?.let { repository.getCategoryType(it) }
            val error = TransactionRules.check(updated, clock.instant(), usable, categoryType)
            return if (error != null) {
                Outcome.Err(error)
            } else {
                try {
                    repository.update(updated)
                    Outcome.Ok(Unit)
                } catch (_: ArithmeticException) {
                    Outcome.Err(TransactionError.AmountOverflow)
                }
            }
        }
    }
