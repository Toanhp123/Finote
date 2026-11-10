package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import javax.inject.Inject

class CountTrashableTransactionsUseCase
    @Inject
    constructor(
        private val repository: AccountRepository,
    ) {
        /** Rows that ignoring [accountId] with "trash old transactions" would soft-delete. */
        suspend operator fun invoke(accountId: String): Int = repository.getTrashableTransactionCount(accountId)
    }
