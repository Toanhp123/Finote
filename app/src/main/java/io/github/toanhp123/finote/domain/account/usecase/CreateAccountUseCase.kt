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
         * Stamps both timestamps from the clock, clears `deletedAt`, puts the account last, balance = initial. A bank
         * account with an unknown start stays provisional ([isStartKnown] false); any other is initialized.
         * Returns the id, or the rule that refused the account.
         */
        suspend operator fun invoke(
            account: Account,
            isStartKnown: Boolean,
        ): Outcome<String, AccountError> {
            val now = clock.millis()
            val fresh =
                account.copy(
                    currentBalance = account.initialBalance,
                    isInitialized = isStartKnown || account.bankPackageName == null,
                    createdAt = now,
                    updatedAt = now,
                    deletedAt = null,
                )
            return tx.write {
                val error = AccountRules.checkNew(fresh)
                if (error != null) {
                    Outcome.Err(error)
                } else {
                    repository.add(fresh.copy(sortOrder = repository.getNextSortOrder()))
                    Outcome.Ok(fresh.id)
                }
            }
        }
    }
