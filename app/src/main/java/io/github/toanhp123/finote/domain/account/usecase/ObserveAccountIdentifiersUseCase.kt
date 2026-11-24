package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveAccountIdentifiersUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) {
        /** The masked numbers linked to [accountId]. */
        operator fun invoke(accountId: String): Flow<List<AccountIdentifier>> = repository.observeIdentifiers(accountId)
    }
