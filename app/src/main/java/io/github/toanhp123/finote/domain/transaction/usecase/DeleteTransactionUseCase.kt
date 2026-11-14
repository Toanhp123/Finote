package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject

class DeleteTransactionUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Soft delete. A missing id is `NotFound`; deleting twice does nothing. */
        suspend operator fun invoke(id: String): Outcome<Unit, TransactionError> =
            tx.write {
                val existing = repository.getTransaction(id)
                when {
                    existing == null -> {
                        Outcome.Err(TransactionError.NotFound)
                    }

                    existing.deletedAt != null -> {
                        Outcome.Ok(Unit)
                    }

                    else -> {
                        // Dropping a row can push a later running balance out of the Long range.
                        try {
                            repository.softDelete(id, clock.millis())
                            Outcome.Ok(Unit)
                        } catch (_: ArithmeticException) {
                            Outcome.Err(TransactionError.AmountOverflow)
                        }
                    }
                }
            }
    }
