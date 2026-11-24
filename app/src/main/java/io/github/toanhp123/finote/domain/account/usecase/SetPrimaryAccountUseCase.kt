package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class SetPrimaryAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Stars the account. A missing or deleted one is `NotFound`; an archived or ignored one is `Unusable`. */
        suspend operator fun invoke(id: String): Outcome<Unit, AccountError> =
            tx.write {
                val old = repository.getAccount(id)
                when {
                    old == null || old.deletedAt != null -> {
                        Outcome.Err(AccountError.NotFound)
                    }

                    !old.isUsable -> {
                        Outcome.Err(AccountError.Unusable)
                    }

                    else -> {
                        repository.setPrimary(id, clock.millis())
                        Outcome.Ok(Unit)
                    }
                }
            }
    }
