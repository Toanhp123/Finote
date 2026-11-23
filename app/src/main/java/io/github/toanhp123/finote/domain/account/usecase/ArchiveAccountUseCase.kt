package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class ArchiveAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Archives or restores an account. A missing or deleted one is `NotFound`. */
        suspend operator fun invoke(
            id: String,
            archived: Boolean,
        ): Outcome<Unit, AccountError> =
            tx.write {
                val old = repository.getAccount(id)
                if (old == null || old.deletedAt != null) {
                    Outcome.Err(AccountError.NotFound)
                } else {
                    repository.setArchived(id, archived, clock.millis())
                    Outcome.Ok(Unit)
                }
            }
    }
