package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import io.github.toanhp123.finote.domain.transaction.model.groupByDay
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import javax.inject.Inject

// shortcut: loads the whole list into memory, fine up to a few thousand rows; move to Paging 3 when the list is
// slow on a real device, or before P2 if notifications create many rows.
class ObserveTransactionListUseCase
    @Inject
    constructor(
        private val repository: TransactionRepository,
        private val accounts: AccountRepository,
        private val clock: Clock,
    ) {
        /**
         * [accountId] null means every account, minus rows of an ignored account so day nets match the report.
         * [pendingOnly] keeps rows awaiting repayment, ignored accounts included, and skips that filter.
         * Days are in the clock's zone, newest first.
         */
        operator fun invoke(
            accountId: String?,
            pendingOnly: Boolean,
        ): Flow<List<TransactionDay>> {
            val source = repository.observeTransactions(accountId)
            val visible =
                if (accountId == null && !pendingOnly) {
                    // Only the ignored-id set matters, so a balance write does not rebuild the list.
                    val ignoredIds =
                        accounts.observeAccounts().map { all -> all.filter { it.isIgnored }.map { it.id }.toSet() }
                    source.combine(ignoredIds.distinctUntilChanged()) { rows, ignored ->
                        rows.filter { it.accountId !in ignored }
                    }
                } else {
                    source
                }
            return visible.map { rows ->
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
    }
