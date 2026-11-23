package io.github.toanhp123.finote.domain.transaction.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Groups [transactions] (newest first) into consecutive local days, keeping their order.
 * [accountId] is the filtered account, null for all. Overflow throws [ArithmeticException].
 */
fun groupByDay(
    transactions: List<Transaction>,
    zone: ZoneId,
    accountId: String?,
): List<TransactionDay> =
    transactions
        .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        .map { (date, rows) -> TransactionDay(date, netOf(rows, accountId), rows) }

private fun netOf(
    rows: List<Transaction>,
    accountId: String?,
): Long {
    var net = 0L
    for (row in rows) {
        if (row.isExcludedFromAnalytics) continue
        net =
            when (row.type) {
                TransactionType.INCOME, TransactionType.REFUND -> Math.addExact(net, row.amount)

                TransactionType.EXPENSE -> Math.subtractExact(net, row.amount)

                // Moving money between the user's own accounts is not spending.
                TransactionType.TRANSFER, TransactionType.ADJUSTMENT -> net
            }
        // The fee leaves the source account, so a transfer seen from its destination carries no fee.
        if (accountId == null || row.accountId == accountId) net = Math.subtractExact(net, row.feeAmount)
    }
    // The UI negates a negative net; Long.MIN_VALUE has no positive twin.
    if (net == Long.MIN_VALUE) throw ArithmeticException("net out of range")
    return net
}

/** One local day of the list. [net] is VND: positive means more came in than went out. */
data class TransactionDay(
    val date: LocalDate,
    val net: Long,
    val transactions: List<Transaction>,
)
