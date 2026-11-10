package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.service.detectBalanceDrift
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// shortcut: O(transactions) per emission, move to a DAO query when it measures slow in P5.
class ObserveBalanceDriftUseCase
    @Inject
    constructor(
        private val observeUsableAccounts: ObserveUsableAccountsUseCase,
        private val transactionRepository: TransactionRepository,
    ) {
        /** One entry per usable account whose balance disagrees with the bank, in the account order. */
        operator fun invoke(): Flow<List<BalanceDrift>> =
            combine(
                observeUsableAccounts(),
                transactionRepository.observeTransactions(null),
            ) { accounts, transactions ->
                accounts.mapNotNull { detectBalanceDrift(it, transactions) }
            }
    }
