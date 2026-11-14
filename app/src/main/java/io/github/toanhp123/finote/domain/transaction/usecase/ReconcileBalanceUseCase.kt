package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class ReconcileBalanceUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Only checks that the account is usable: the adjustment amount is only known after the repository reads the
         * balance. Returns `Ok(null)` when the balance already matches.
         */
        suspend operator fun invoke(
            accountId: String,
            actualBalance: Long,
        ): Outcome<Transaction?, TransactionError> =
            tx.write {
                if (accountId !in repository.getUsableAccountIds(setOf(accountId))) {
                    Outcome.Err(TransactionError.AccountUnusable)
                } else {
                    try {
                        val id = UUID.randomUUID().toString()
                        Outcome.Ok(repository.reconcile(id, accountId, actualBalance, clock.millis()))
                    } catch (_: ArithmeticException) {
                        Outcome.Err(TransactionError.AmountOverflow)
                    }
                }
            }
    }
