package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.TransactionDay
import io.github.toanhp123.finote.domain.transaction.groupByDay
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
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
        /**
         * [accountId] null means every account. [pendingOnly] keeps rows awaiting repayment, filtered before grouping.
         * Days are in the clock's zone, newest first.
         */
        operator fun invoke(
            accountId: String?,
            pendingOnly: Boolean,
        ): Flow<List<TransactionDay>> =
            repository.observeTransactions(accountId).map { rows ->
                val shown =
                    if (pendingOnly) {
                        rows.filter {
                            it.reimbursementStatus == ReimbursementStatus.PENDING
                        }
                    } else {
                        rows
                    }
                groupByDay(shown, clock.zone, accountId)
            }
    }
