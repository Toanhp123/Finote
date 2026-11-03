package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject

class UpdateTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val validate: ValidateTransactionUseCase,
        private val clock: Clock,
    ) {
        /** Replaces a live transaction. Keeps `createdAt`, stamps `updatedAt`. A missing or deleted one is rejected. */
        suspend operator fun invoke(transaction: Transaction) {
            val existing = repository.getTransaction(transaction.id)
            require(existing != null && existing.deletedAt == null) { "transaction does not exist or is deleted" }
            val old = setOfNotNull(existing.accountId, existing.toAccountId)
            require(
                repository.getActiveAccountIds(old).containsAll(old),
            ) { "account does not exist, is deleted or archived" }
            val updated =
                transaction.copy(deletedAt = null, createdAt = existing.createdAt, updatedAt = clock.millis())
            validate(updated)
            repository.update(updated)
        }
    }
