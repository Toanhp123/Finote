package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

class CreateAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val validate: ValidateAccountUseCase,
        private val clock: Clock,
    ) {
        /** Stamps both timestamps from the clock, clears `deletedAt`, marks it initialized, balance = initial. */
        suspend operator fun invoke(account: Account) {
            val now = clock.millis()
            val fresh =
                account.copy(
                    currentBalance = account.initialBalance,
                    isInitialized = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            validate(fresh)
            repository.add(fresh)
        }
    }
