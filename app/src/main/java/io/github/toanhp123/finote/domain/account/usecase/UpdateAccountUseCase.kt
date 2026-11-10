package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class UpdateAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val validate: ValidateAccountUseCase,
        private val clock: Clock,
    ) {
        /** Stamps `updatedAt` and validates. The repository keeps every field it does not list as editable. */
        suspend operator fun invoke(account: Account) {
            val updated = account.copy(updatedAt = clock.millis())
            validate(updated)
            repository.update(updated)
        }
    }
