package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class ReorderAccountsUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Gives each id the sort order of its index. Ids not listed keep theirs. */
        suspend operator fun invoke(orderedIds: List<String>): Outcome<Unit, AccountError> =
            tx.write {
                repository.reorder(orderedIds, clock.millis())
                Outcome.Ok(Unit)
            }
    }
