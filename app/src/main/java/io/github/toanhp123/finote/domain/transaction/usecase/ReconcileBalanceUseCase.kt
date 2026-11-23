package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.account.repository.DriftAcknowledgementRepository
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
        private val acks: DriftAcknowledgementRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Only checks that the account is usable: the adjustment amount is only known after the repository reads the
         * balance. Returns `Ok(null)` when the balance already matches; the drift key is then acknowledged, so the
         * drift banner stays hidden until the ledger or the newest bank row changes.
         */
        suspend operator fun invoke(
            accountId: String,
            actualBalance: Long,
        ): Outcome<Transaction?, TransactionError> {
            var key: DriftKey? = null
            val outcome =
                tx.write {
                    if (accountId !in repository.getUsableAccountIds(setOf(accountId))) {
                        Outcome.Err(TransactionError.AccountUnusable)
                    } else {
                        try {
                            val id = UUID.randomUUID().toString()
                            val adjustment = repository.reconcile(id, accountId, actualBalance, clock.millis())
                            // Read in the same transaction, so a row landing after the commit is not acknowledged.
                            key = if (adjustment == null) repository.getDriftKey(accountId) else null
                            Outcome.Ok(adjustment)
                        } catch (_: ArithmeticException) {
                            Outcome.Err(TransactionError.AmountOverflow)
                        }
                    }
                }
            // Outside the transaction: a preferences write cannot roll back with the database.
            if (outcome is Outcome.Ok) key?.let { acks.acknowledge(accountId, it) }
            return outcome
        }
    }
