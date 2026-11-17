package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.repository.TransferPairRepository
import java.time.Clock
import javax.inject.Inject

class UnpairTransferUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val pairs: TransferPairRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Splits an auto-paired transfer back into its two original rows. `NotFound` when [transferId] is missing,
         * deleted, or was not made by pairing; `AccountUnusable` when an original's account cannot take rows now.
         */
        suspend operator fun invoke(transferId: String): Outcome<Unit, TransactionError> =
            tx.write {
                val live = repository.getTransaction(transferId)?.takeIf { it.deletedAt == null }
                val originals = if (live == null) emptyList() else pairs.getPairedOriginals(transferId)
                val accountIds = originals.mapTo(mutableSetOf()) { it.accountId }
                when {
                    originals.isEmpty() -> {
                        Outcome.Err(TransactionError.NotFound)
                    }

                    repository.getUsableAccountIds(accountIds) != accountIds -> {
                        Outcome.Err(TransactionError.AccountUnusable)
                    }

                    else -> {
                        try {
                            pairs.unpair(transferId, clock.millis())
                            Outcome.Ok(Unit)
                        } catch (_: ArithmeticException) {
                            Outcome.Err(TransactionError.AmountOverflow)
                        }
                    }
                }
            }
    }
