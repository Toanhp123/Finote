package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class SetAccountIgnoredUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Ignores or follows [id]. Trashing old rows applies only when ignoring. Missing or deleted is `NotFound`. */
        suspend operator fun invoke(
            id: String,
            ignored: Boolean,
            shouldTrashOldTransactions: Boolean,
        ): Outcome<Unit, AccountError> =
            tx.write {
                val old = repository.getAccount(id)
                if (old == null || old.deletedAt != null) {
                    Outcome.Err(AccountError.NotFound)
                } else {
                    repository.setIgnored(id, ignored, shouldTrashOldTransactions, clock.millis())
                    Outcome.Ok(Unit)
                }
            }
    }
