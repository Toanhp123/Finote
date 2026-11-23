package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.AccountRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class DeleteAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Soft delete. An unknown id is `NotFound`; deleting an already deleted account does nothing. */
        suspend operator fun invoke(id: String): Outcome<Unit, AccountError> =
            tx.write {
                val old = repository.getAccount(id)
                when {
                    old == null -> {
                        Outcome.Err(AccountError.NotFound)
                    }

                    old.deletedAt != null -> {
                        Outcome.Ok(Unit)
                    }

                    else -> {
                        val error = AccountRules.checkDelete(repository.getLiveTransactionCount(id))
                        if (error != null) {
                            Outcome.Err(error)
                        } else {
                            repository.softDelete(id, clock.millis())
                            Outcome.Ok(Unit)
                        }
                    }
                }
            }
    }
