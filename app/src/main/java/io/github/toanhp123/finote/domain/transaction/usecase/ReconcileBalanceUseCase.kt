package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class ReconcileBalanceUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val clock: Clock,
    ) {
        /**
         * Skips [ValidateTransactionUseCase]: the amount is only known after the repository reads the balance.
         * Returns null when the balance already matches.
         */
        suspend operator fun invoke(
            accountId: String,
            actualBalance: Long,
        ): Transaction? = repository.reconcile(UUID.randomUUID().toString(), accountId, actualBalance, clock.millis())
    }
