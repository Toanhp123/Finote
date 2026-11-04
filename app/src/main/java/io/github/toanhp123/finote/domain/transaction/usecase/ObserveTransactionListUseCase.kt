package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.TransactionDay
import io.github.toanhp123.finote.domain.transaction.groupByDay
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

// shortcut: loads the whole list into memory, fine up to a few thousand rows; move to Paging 3 when the list is
// slow on a real device, or before P2 if notifications create many rows.
class ObserveTransactionListUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val clock: Clock,
    ) {
        /** [accountId] null means every account. Days are in the clock's zone, newest first. */
        operator fun invoke(accountId: String?): Flow<List<TransactionDay>> =
            repository.observeTransactions(accountId).map { groupByDay(it, clock.zone, accountId) }
    }
