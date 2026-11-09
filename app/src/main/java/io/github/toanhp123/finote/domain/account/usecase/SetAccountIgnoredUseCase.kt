package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class SetAccountIgnoredUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val clock: Clock,
    ) {
        /** Ignores or follows [id]. Trashing old rows applies only when ignoring. */
        suspend operator fun invoke(
            id: String,
            ignored: Boolean,
            shouldTrashOldTransactions: Boolean,
        ) {
            repository.setIgnored(id, ignored, shouldTrashOldTransactions, clock.millis())
        }
    }
