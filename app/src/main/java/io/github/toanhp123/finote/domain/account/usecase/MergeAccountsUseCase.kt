package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.AccountRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class MergeAccountsUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /** Merges [sourceId] into [targetId], or returns the rule that refused it. */
        suspend operator fun invoke(
            sourceId: String,
            targetId: String,
        ): Outcome<Unit, AccountError> =
            tx.write {
                val rejection =
                    AccountRules.checkMerge(
                        sourceId,
                        targetId,
                        repository.getAccount(sourceId),
                        repository.getAccount(targetId),
                        repository.countTransfersBetween(sourceId, targetId),
                    )
                if (rejection != null) {
                    Outcome.Err(AccountError.MergeRejected(rejection))
                } else {
                    repository.merge(sourceId, targetId, clock.millis())
                    Outcome.Ok(Unit)
                }
            }
    }
