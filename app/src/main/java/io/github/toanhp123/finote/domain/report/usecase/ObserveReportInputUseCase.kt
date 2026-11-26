package io.github.toanhp123.finote.domain.report.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject

/** Live rows of [from]..[toInclusive] with the accounts and categories a report needs, re-emitted on any change. */
class ObserveReportInputUseCase
    @Inject
    constructor(
        private val transactions: TransactionRepository,
        private val accounts: AccountRepository,
        private val categories: CategoryRepository,
        private val clock: Clock,
    ) {
        operator fun invoke(
            from: YearMonth,
            toInclusive: YearMonth,
        ): Flow<ReportInput> {
            // The zone is read once for the query; the calculator re-filters by month with the zone of each emission.
            val zone = clock.zone
            val start =
                from
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            val end =
                toInclusive
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()
            // Only the ignored-id set matters, so a balance write does not recompute the report.
            val ignoredIds =
                accounts.observeAccounts().map { all -> all.filter { it.isIgnored }.map { it.id }.toSet() }
            return combine(
                transactions.observeTransactionsBetween(start, end),
                ignoredIds.distinctUntilChanged(),
                categories.observeCategories(),
            ) { rows, ignored, categoryList ->
                ReportInput(
                    transactions = rows,
                    ignoredAccountIds = ignored,
                    categories = categoryList,
                    zone = clock.zone,
                )
            }
        }
    }
