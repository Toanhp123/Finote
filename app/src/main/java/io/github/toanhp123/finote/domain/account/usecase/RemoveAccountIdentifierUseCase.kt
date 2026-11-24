package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import javax.inject.Inject

class RemoveAccountIdentifierUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
        private val tx: TransactionRunner,
    ) {
        /**
         * Hard-deletes the key, unless its owner is archived (`Archived`). A key of a missing or deleted owner is still
         * removed.
         */
        suspend operator fun invoke(identifier: AccountIdentifier): Outcome<Unit, AccountError> =
            tx.write {
                if (repository.getAccount(identifier.accountId)?.isArchived == true) {
                    Outcome.Err(AccountError.Archived)
                } else {
                    repository.removeIdentifier(identifier)
                    Outcome.Ok(Unit)
                }
            }
    }
