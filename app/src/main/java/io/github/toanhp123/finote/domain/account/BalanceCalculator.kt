package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.MonthlyBalance
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Pure balance math. Same formula for every account type. Long overflow throws [ArithmeticException]. */
object BalanceCalculator {
    fun balance(
        accountId: String,
        initialBalance: Long,
        transactions: List<Transaction>,
    ): Long = Math.addExact(initialBalance, totalsOf(accountId, transactions).net())

    /**
     * Contiguous months from the first relevant transaction through max(untilMonth, last transaction month),
     * so a transaction after untilMonth is never dropped. No relevant transaction gives an empty list.
     */
    fun monthlySnapshots(
        accountId: String,
        initialBalance: Long,
        transactions: List<Transaction>,
        zone: ZoneId,
        untilMonth: YearMonth,
    ): List<MonthlyBalance> {
        val byMonth =
            transactions
                .filter { it.touches(accountId) }
                .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
        if (byMonth.isEmpty()) return emptyList()

        val last = maxOf(byMonth.keys.max(), untilMonth)
        val snapshots = mutableListOf<MonthlyBalance>()
        var opening = initialBalance
        var month = byMonth.keys.min()
        while (month <= last) {
            val totals = totalsOf(accountId, byMonth[month].orEmpty())
            val closing = Math.addExact(opening, totals.net())
            snapshots += totals.toSnapshot(accountId, month.toString(), opening, closing)
            opening = closing
            month = month.plusMonths(1)
        }
        return snapshots
    }

    private fun totalsOf(
        accountId: String,
        transactions: List<Transaction>,
    ): Totals =
        transactions
            .filter { it.touches(accountId) }
            .fold(Totals()) { acc, tx -> acc + tx.totalsFor(accountId) }

    private fun Transaction.touches(accountId: String): Boolean =
        deletedAt == null &&
            (this.accountId == accountId || (type == TransactionType.TRANSFER && toAccountId == accountId))

    /** The fee is always charged to the source account, so it counts as expense there only. */
    private fun Transaction.totalsFor(accountId: String): Totals {
        var totals = Totals()
        if (this.accountId == accountId) {
            totals =
                when (type) {
                    TransactionType.INCOME, TransactionType.REFUND -> {
                        Totals(income = amount)
                    }

                    TransactionType.EXPENSE -> {
                        Totals(expense = amount)
                    }

                    TransactionType.ADJUSTMENT -> {
                        adjustmentTotals()
                    }

                    TransactionType.TRANSFER -> {
                        Totals(transferOut = amount)
                    }
                } + Totals(expense = feeAmount)
        }
        if (type == TransactionType.TRANSFER && toAccountId == accountId) {
            totals += Totals(transferIn = amount)
        }
        return totals
    }

    private fun Transaction.adjustmentTotals(): Totals =
        when (adjustmentDirection) {
            AdjustmentDirection.IN -> Totals(income = amount)
            AdjustmentDirection.OUT -> Totals(expense = amount)
            null -> error("unreachable: validated in Transaction")
        }

    private data class Totals(
        val income: Long = 0,
        val expense: Long = 0,
        val transferIn: Long = 0,
        val transferOut: Long = 0,
    ) {
        operator fun plus(other: Totals) =
            Totals(
                income = Math.addExact(income, other.income),
                expense = Math.addExact(expense, other.expense),
                transferIn = Math.addExact(transferIn, other.transferIn),
                transferOut = Math.addExact(transferOut, other.transferOut),
            )

        fun net(): Long =
            Math.subtractExact(Math.addExact(Math.subtractExact(income, expense), transferIn), transferOut)

        fun toSnapshot(
            accountId: String,
            monthYear: String,
            opening: Long,
            closing: Long,
        ) = MonthlyBalance(
            accountId = accountId,
            monthYear = monthYear,
            openingBalance = opening,
            closingBalance = closing,
            totalIncome = income,
            totalExpense = expense,
            totalTransferIn = transferIn,
            totalTransferOut = transferOut,
        )
    }
}
