package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import javax.inject.Inject

class GetTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
    ) {
        /** The live row, or null when it is missing or soft-deleted. */
        suspend operator fun invoke(id: String): Transaction? =
            repository.getTransaction(id)?.takeIf { it.deletedAt == null }
    }
