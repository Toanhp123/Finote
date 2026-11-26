package io.github.toanhp123.finote.domain.report.model

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import java.time.YearMonth

/**
 * One month of reporting. Money is VND. [expense] is net of refunds and includes fees; [net] is [income] minus it.
 * [buckets] add up to [expense]. [expenseByCategory] is keyed by leaf category id and leaves out uncategorized rows.
 * [hasFutureRows] is true when the month is the current one and holds a row dated after today.
 */
data class MonthReport(
    val month: YearMonth,
    val income: Long,
    val expense: Long,
    val net: Long,
    val buckets: List<BucketTotal>,
    val merchants: List<MerchantTotal>,
    val expenseByCategory: Map<String, Long>,
    val pace: Pace,
    val hasFutureRows: Boolean,
) {
    // Money and merchant names must never reach a log or crash report.
    override fun toString() = "MonthReport(month=$month)"
}

/** [filter] is a `ByCategory`, `Uncategorized` or `TransferFees`. [amount] may be zero or negative. */
data class BucketTotal(
    val filter: ReportFilter,
    val amount: Long,
) {
    override fun toString() = "BucketTotal(filter=$filter)"
}

data class MerchantTotal(
    val name: String,
    val amount: Long,
) {
    override fun toString() = "MerchantTotal"
}

/** Cumulative spending per day, this month against last. [percent] is null when there is no basis to compare. */
data class Pace(
    val percent: Int?,
    val thisMonth: List<Long>,
    val lastMonth: List<Long>,
) {
    override fun toString() = "Pace(days=${thisMonth.size})"
}

/** One transaction as it counts in a slice: [amount] is its share in VND, negative for a refund. */
data class ReportRow(
    val transaction: Transaction,
    val amount: Long,
) {
    override fun toString() = "ReportRow(transaction=$transaction)"
}

data class ReportRows(
    val rows: List<ReportRow>,
    val total: Long,
) {
    override fun toString() = "ReportRows(rows=${rows.size})"
}
