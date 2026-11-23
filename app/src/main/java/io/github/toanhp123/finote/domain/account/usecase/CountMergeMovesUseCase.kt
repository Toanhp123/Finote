package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import javax.inject.Inject

class CountMergeMovesUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) {
        /** Live transactions a merge of [accountId] would move. */
        suspend operator fun invoke(accountId: String): Int = repository.getLiveTransactionCount(accountId)
    }
