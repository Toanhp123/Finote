package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject

class DeleteTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val clock: Clock,
    ) {
        /** Soft delete. A missing id is rejected; deleting twice does nothing. */
        suspend operator fun invoke(id: String) {
            val existing = repository.getTransaction(id)
            requireNotNull(existing) { "transaction does not exist" }
            if (existing.deletedAt == null) repository.softDelete(id, clock.millis())
        }
    }
