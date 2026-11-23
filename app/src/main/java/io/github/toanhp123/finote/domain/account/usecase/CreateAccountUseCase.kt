package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.service.AccountRules
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class CreateAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Stamps both timestamps from the clock, clears `deletedAt`, marks it initialized, balance = initial.
         * Returns the id, or the rule that refused the account.
         */
        suspend operator fun invoke(account: Account): Outcome<String, AccountError> {
            val now = clock.millis()
            val fresh =
                account.copy(
                    currentBalance = account.initialBalance,
                    isInitialized = true,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            return tx.write {
                val error = AccountRules.checkNew(fresh)
                if (error != null) {
                    Outcome.Err(error)
                } else {
                    repository.add(fresh)
                    Outcome.Ok(fresh.id)
                }
            }
        }
    }
