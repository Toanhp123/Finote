package io.github.toanhp123.finote.domain.report.service

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.MerchantTotal
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.Pace
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.report.model.ReportRow
import io.github.toanhp123.finote.domain.report.model.ReportRows
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

private const val TOP_MERCHANTS = 5
private const val PERCENT = 100L

/**
 * The one place that decides what counts as income and expense in a report. Pure math. Long overflow throws
 * [ArithmeticException]. A refund lowers its bucket and the expense; a transfer adds only its fee, like `groupByDay`.
 */
object ReportCalculator {
    fun monthReport(
        input: ReportInput,
        month: YearMonth,
        today: LocalDate,
    ): MonthReport {
        val kept = keptRows(input, month)
        val entries = entriesOf(kept, input.categories)
        val income = kept.filter { it.type == TransactionType.INCOME }.sumExact { it.amount }
        val expense = entries.sumExact { it.amount }
        return MonthReport(
            month = month,
            income = income,
            expense = expense,
            net = Math.subtractExact(income, expense),
            buckets = bucketsOf(entries),
            merchants = merchantsOf(entries),
            expenseByCategory = expenseByCategoryOf(entries),
            pace = paceOf(input, month, today, entries),
            hasFutureRows = month == YearMonth.from(today) && kept.any { it.localDate(input.zone) > today },
        )
    }

    /** The rows behind one [filter] of [month], newest first, with their share of it. Totals match `monthReport`. */
    fun rows(
        input: ReportInput,
        month: YearMonth,
        filter: ReportFilter,
    ): ReportRows {
        val entries = entriesOf(keptRows(input, month), input.categories)
        val picked =
            when (filter) {
                is ReportFilter.ByMerchant -> entries.filter { it.isPrincipal && it.tx.merchantName == filter.name }
                ReportFilter.AllSpending -> entries
                else -> entries.filter { it.bucket == filter }
            }
        val rows =
            picked
                .sortedWith(
                    compareByDescending<Entry> { it.tx.timestamp }
                        .thenByDescending { it.tx.createdAt }
                        .thenByDescending { it.tx.id },
                ).map { ReportRow(it.tx, it.amount) }
        return ReportRows(rows, rows.sumExact { it.amount })
    }

    /** Spending up to the same day of the previous month. A future row of the current month stays off the curve. */
    private fun paceOf(
        input: ReportInput,
        month: YearMonth,
        today: LocalDate,
        entries: List<Entry>,
    ): Pace {
        val cutoff = if (month < YearMonth.from(today)) month.lengthOfMonth() else today.dayOfMonth
        val previous = month.minusMonths(1)
        val lastEntries = entriesOf(keptRows(input, previous), input.categories)
        val thisMonth = cumulative(entries, cutoff, input.zone)
        val lastDays = minOf(cutoff, previous.lengthOfMonth())
        val lastMonth = if (lastEntries.isEmpty()) emptyList() else cumulative(lastEntries, lastDays, input.zone)
        val last = lastMonth.lastOrNull()
        val percent =
            if (last == null || last <= 0) {
                null
            } else {
                BigDecimal
                    .valueOf(Math.subtractExact(thisMonth.last(), last))
                    .multiply(BigDecimal.valueOf(PERCENT))
                    .divide(BigDecimal.valueOf(last), 0, RoundingMode.HALF_UP)
                    .takeIf { it.abs() <= BigDecimal(Int.MAX_VALUE) } // a tiny baseline is no real baseline
                    ?.toInt()
            }
        return Pace(percent, thisMonth, lastMonth)
    }

    /** Running total for days 1..[days]; a day with no entry repeats the day before. */
    private fun cumulative(
        entries: List<Entry>,
        days: Int,
        zone: ZoneId,
    ): List<Long> {
        val perDay = entries.groupBy { it.tx.localDate(zone).dayOfMonth }
        var total = 0L
        return (1..days).map { day ->
            total = Math.addExact(total, perDay[day].orEmpty().sumExact { it.amount })
            total
        }
    }

    /** One slice of spending: [amount] is negative for a refund. A fee entry sits in `TransferFees`. */
    private class Entry(
        val tx: Transaction,
        val bucket: ReportFilter,
        val amount: Long,
    ) {
        val isPrincipal get() = bucket != ReportFilter.TransferFees
    }

    private fun keptRows(
        input: ReportInput,
        month: YearMonth,
    ): List<Transaction> =
        input.transactions.filter {
            it.deletedAt == null &&
                !it.isExcludedFromAnalytics &&
                it.accountId !in input.ignoredAccountIds &&
                YearMonth.from(it.localDate(input.zone)) == month
        }

    private fun entriesOf(
        kept: List<Transaction>,
        categories: List<Category>,
    ): List<Entry> {
        val byId = categories.associateBy { it.id }
        return kept.flatMap { tx ->
            val principal =
                when (tx.type) {
                    TransactionType.EXPENSE -> Entry(tx, bucketOf(tx, byId), tx.amount)
                    TransactionType.REFUND -> Entry(tx, bucketOf(tx, byId), -tx.amount)
                    TransactionType.INCOME, TransactionType.TRANSFER, TransactionType.ADJUSTMENT -> null
                }
            val fee = if (tx.feeAmount > 0) Entry(tx, ReportFilter.TransferFees, tx.feeAmount) else null
            listOfNotNull(principal, fee)
        }
    }

    /** A child rolls into its parent when the parent is known; an unknown category is uncategorized. */
    private fun bucketOf(
        tx: Transaction,
        byId: Map<String, Category>,
    ): ReportFilter {
        val category = tx.categoryId?.let { byId[it] } ?: return ReportFilter.Uncategorized
        val parentId = category.parentId?.takeIf { it in byId }
        return ReportFilter.ByCategory(parentId ?: category.id)
    }

    private fun bucketsOf(entries: List<Entry>): List<BucketTotal> =
        entries
            .groupBy { it.bucket }
            .map { (filter, group) -> BucketTotal(filter, group.sumExact { it.amount }) }
            .sortedWith(
                compareByDescending<BucketTotal> { it.amount }
                    .thenBy { rank(it.filter) }
                    .thenBy { (it.filter as? ReportFilter.ByCategory)?.categoryId.orEmpty() },
            )

    private fun rank(filter: ReportFilter) =
        when (filter) {
            is ReportFilter.ByCategory -> 0
            ReportFilter.Uncategorized -> 1
            ReportFilter.TransferFees -> 2
            is ReportFilter.ByMerchant, ReportFilter.AllSpending -> error("this filter is not a bucket")
        }

    private fun merchantsOf(entries: List<Entry>): List<MerchantTotal> =
        entries
            .filter { it.isPrincipal && !it.tx.merchantName.isNullOrBlank() }
            .groupBy { it.tx.merchantName.orEmpty() }
            .map { (name, group) -> MerchantTotal(name, group.sumExact { it.amount }) }
            .filter { it.amount > 0 }
            .sortedWith(compareByDescending<MerchantTotal> { it.amount }.thenBy { it.name })
            .take(TOP_MERCHANTS)

    /** Keyed by leaf id. Only a category found in the list counts; a missing one is uncategorized alone. */
    private fun expenseByCategoryOf(entries: List<Entry>): Map<String, Long> =
        entries
            .filter { it.bucket is ReportFilter.ByCategory }
            .groupBy { it.tx.categoryId.orEmpty() }
            .mapValues { (_, group) -> group.sumExact { it.amount } }

    private fun <T> List<T>.sumExact(amount: (T) -> Long): Long =
        fold(0L) { acc, item -> Math.addExact(acc, amount(item)) }

    private fun Transaction.localDate(zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
}
