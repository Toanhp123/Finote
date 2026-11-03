package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class DeleteAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val clock: Clock,
    ) {
        /** Soft delete. The repository rejects an account that still has live transactions. */
        suspend operator fun invoke(id: String) {
            repository.softDelete(id, clock.millis())
        }
    }
