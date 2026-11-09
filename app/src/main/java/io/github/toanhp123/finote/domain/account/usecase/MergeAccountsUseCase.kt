package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class MergeAccountsUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val clock: Clock,
    ) {
        /** Merges [sourceId] into [targetId]. The repository rejects with `AccountMergeRejectedException`. */
        suspend operator fun invoke(
            sourceId: String,
            targetId: String,
        ) {
            repository.merge(sourceId, targetId, clock.millis())
        }
    }
