package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.account.model.digest
import io.github.toanhp123.finote.domain.account.repository.DriftAcknowledgementRepository
import io.github.toanhp123.finote.domain.account.service.balanceDriftKey
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
        private val acknowledgements: DriftAcknowledgementRepository,
    ) {
        /** One entry per usable account that may differ from the bank and whose drift the user has not checked. */
        operator fun invoke(): Flow<List<BalanceDrift>> =
            combine(
                observeUsableAccounts(),
                transactionRepository.observeTransactions(null),
                acknowledgements.observe(),
            ) { accounts, transactions, acknowledged ->
                // An account whose ledger overflows Long has no usable drift; it must not stop the others.
                accounts.mapNotNull {
                    try {
                        val key = balanceDriftKey(it, transactions)
                        if (key != null && key.digest(it.id) != acknowledged[it.id]) BalanceDrift(it.id) else null
                    } catch (_: ArithmeticException) {
                        null
                    }
                }
            }
    }
