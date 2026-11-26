package io.github.toanhp123.finote.domain.report.usecase

import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** True while the user has at least one live transaction; the dashboard shows its empty state otherwise. */
class ObserveHasTransactionsUseCase
    @Inject
    constructor(
        private val transactions: TransactionRepository,
    ) {
        operator fun invoke(): Flow<Boolean> = transactions.observeHasTransactions()
    }
