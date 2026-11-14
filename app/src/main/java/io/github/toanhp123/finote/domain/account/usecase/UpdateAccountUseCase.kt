package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import java.time.Clock
import javax.inject.Inject

class UpdateAccountUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Stamps `updatedAt`. Only the name is checked: currency is not editable. The repository keeps every field it
         * does not list as editable.
         */
        suspend operator fun invoke(account: Account): Outcome<Unit, AccountError> {
            val updated = account.copy(updatedAt = clock.millis())
            return tx.write {
                val old = repository.getAccount(updated.id)
                when {
                    old == null || old.deletedAt != null -> {
                        Outcome.Err(AccountError.NotFound)
                    }

                    updated.name.isBlank() -> {
                        Outcome.Err(AccountError.BlankName)
                    }

                    else -> {
                        repository.update(updated)
                        Outcome.Ok(Unit)
                    }
                }
            }
        }
    }
