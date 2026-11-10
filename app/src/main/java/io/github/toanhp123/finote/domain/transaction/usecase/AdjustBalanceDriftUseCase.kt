package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class AdjustBalanceDriftUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val clock: Clock,
    ) {
        /**
         * Skips [ValidateTransactionUseCase]: the amount is only known after the repository reads the ledger.
         * Returns null when the account has no drift.
         */
        suspend operator fun invoke(accountId: String): Transaction? =
            repository.adjustDrift(UUID.randomUUID().toString(), accountId, clock.millis())
    }
