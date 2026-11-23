package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import java.time.Clock
import javax.inject.Inject

class AddTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Stamps `createdAt` and `updatedAt` from the clock and clears `deletedAt`. Returns the refusing rule. */
        suspend operator fun invoke(transaction: Transaction): Outcome<Unit, TransactionError> {
            val now = clock.instant()
            val stamp = now.toEpochMilli()
            val fresh = transaction.copy(deletedAt = null, createdAt = stamp, updatedAt = stamp)
            return tx.write {
                val usable = repository.getUsableAccountIds(setOfNotNull(fresh.accountId, fresh.toAccountId))
                val categoryType = fresh.categoryId?.let { repository.getCategoryType(it) }
                val error = TransactionRules.check(fresh, now, usable, categoryType)
                if (error != null) {
                    Outcome.Err(error)
                } else {
                    try {
                        repository.add(fresh)
                        Outcome.Ok(Unit)
                    } catch (_: ArithmeticException) {
                        Outcome.Err(TransactionError.AmountOverflow)
                    }
                }
            }
        }
    }
