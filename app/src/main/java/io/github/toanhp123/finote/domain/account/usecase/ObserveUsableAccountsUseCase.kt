package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ObserveUsableAccountsUseCase
    @Inject
    constructor(
        private val observeAccounts: ObserveAccountsUseCase,
    ) {
        /** Accounts a transaction may touch: not deleted, archived or ignored. Order is kept. */
        operator fun invoke(): Flow<List<Account>> = observeAccounts().map { list -> list.filter { it.isUsable } }
    }
