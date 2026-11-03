package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject

class AddTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val validate: ValidateTransactionUseCase,
        private val clock: Clock,
    ) {
        /** Stamps `createdAt` and `updatedAt` from the clock and clears `deletedAt`. */
        suspend operator fun invoke(transaction: Transaction) {
            val now = clock.millis()
            val fresh = transaction.copy(deletedAt = null, createdAt = now, updatedAt = now)
            validate(fresh)
            repository.add(fresh)
        }
    }
