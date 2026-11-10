package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAccountsUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) {
        /** Live accounts, archived and ignored ones included, in the repository's order. */
        operator fun invoke(): Flow<List<Account>> = repository.observeAccounts()
    }
