package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.model.MonthlyBalance
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class MonthlySnapshotsTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private val hcm: ZoneId = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun snapshots(
        txs: List<Transaction>,
        until: String,
        zone: ZoneId = utc,
        initial: Long = 1_000_000,
    ): List<MonthlyBalance> = BalanceCalculator.monthlySnapshots(A, initial, txs, zone, YearMonth.parse(until))

    @Test
    fun `no relevant transactions gives empty list`() {
        val other = tx(TransactionType.EXPENSE, 1_000, account = C, at = at("2026-01-10T00:00:00Z"))
        val deleted = tx(TransactionType.EXPENSE, 1_000, at = at("2026-01-10T00:00:00Z")).copy(deletedAt = 1L)
        assertTrue(snapshots(listOf(other, deleted), "2026-03").isEmpty())
    }

    @Test
    fun `month totals and invariant`() {
        val txs =
            listOf(
                tx(TransactionType.INCOME, 500_000, at = at("2026-01-05T00:00:00Z")),
                tx(TransactionType.EXPENSE, 100_000, fee = 1_000, at = at("2026-01-06T00:00:00Z")),
                tx(TransactionType.TRANSFER, 200_000, fee = 2_000, to = B, at = at("2026-01-07T00:00:00Z")),
                tx(TransactionType.TRANSFER, 50_000, account = B, to = A, at = at("2026-01-08T00:00:00Z")),
            )
        val jan = snapshots(txs, "2026-01").single()
        assertEquals("2026-01", jan.monthYear)
        assertEquals(1_000_000L, jan.openingBalance)
        assertEquals(500_000L, jan.totalIncome)
        assertEquals(103_000L, jan.totalExpense)
        assertEquals(50_000L, jan.totalTransferIn)
        assertEquals(200_000L, jan.totalTransferOut)
        assertEquals(
            jan.openingBalance + jan.totalIncome - jan.totalExpense + jan.totalTransferIn - jan.totalTransferOut,
            jan.closingBalance,
        )
    }

    @Test
    fun `months chain and gaps are filled`() {
        val txs =
            listOf(
                tx(TransactionType.INCOME, 100_000, at = at("2026-01-15T00:00:00Z")),
                tx(TransactionType.EXPENSE, 30_000, at = at("2026-04-15T00:00:00Z")),
            )
        val result = snapshots(txs, "2026-04")
        assertEquals(listOf("2026-01", "2026-02", "2026-03", "2026-04"), result.map { it.monthYear })
        assertEquals(1_100_000L, result[0].closingBalance)
        listOf(result[1], result[2]).forEach {
            assertEquals(1_100_000L, it.openingBalance)
            assertEquals(1_100_000L, it.closingBalance)
            assertEquals(0L, it.totalIncome + it.totalExpense + it.totalTransferIn + it.totalTransferOut)
        }
        assertEquals(1_100_000L, result[3].openingBalance)
        assertEquals(1_070_000L, result[3].closingBalance)
    }

    @Test
    fun `month boundary depends on zone`() {
        val tx = tx(TransactionType.EXPENSE, 10_000, at = at("2026-01-31T20:00:00Z"))
        assertEquals("2026-01", snapshots(listOf(tx), "2026-01", zone = utc).single().monthYear)
        assertEquals("2026-02", snapshots(listOf(tx), "2026-02", zone = hcm).single().monthYear)
    }

    @Test
    fun `fills through untilMonth after last transaction`() {
        val tx = tx(TransactionType.INCOME, 10_000, at = at("2026-01-05T00:00:00Z"))
        val result = snapshots(listOf(tx), "2026-03")
        assertEquals(3, result.size)
        assertEquals(1_010_000L, result.last().closingBalance)
    }

    @Test
    fun `transaction after untilMonth extends the range`() {
        val tx = tx(TransactionType.INCOME, 10_000, at = at("2026-05-05T00:00:00Z"))
        assertEquals("2026-05", snapshots(listOf(tx), "2026-02").last().monthYear)
    }

    @Test
    fun `last closing equals balance`() {
        val txs =
            listOf(
                tx(TransactionType.INCOME, 500_000, at = at("2025-11-05T00:00:00Z")),
                tx(TransactionType.EXPENSE, 120_000, fee = 500, at = at("2026-02-06T00:00:00Z")),
                tx(TransactionType.TRANSFER, 80_000, fee = 1_000, to = B, at = at("2026-03-07T00:00:00Z")),
                tx(TransactionType.EXPENSE, 9_000, at = at("2026-03-08T00:00:00Z")).copy(deletedAt = 1L),
            )
        val expected = BalanceCalculator.balance(A, 1_000_000, txs)
        assertEquals(expected, snapshots(txs, "2026-03").last().closingBalance)
    }

    @Test
    fun `refund and adjustment in count as income, adjustment out as expense`() {
        val txs =
            listOf(
                tx(TransactionType.REFUND, 10_000, at = at("2026-01-05T00:00:00Z")),
                adjustment(AdjustmentDirection.IN, 20_000, at = at("2026-01-06T00:00:00Z")),
                adjustment(AdjustmentDirection.OUT, 5_000, at = at("2026-01-07T00:00:00Z")),
            )
        val jan = snapshots(txs, "2026-01").single()
        assertEquals(30_000L, jan.totalIncome)
        assertEquals(5_000L, jan.totalExpense)
        assertEquals(1_025_000L, jan.closingBalance)
    }

    @Test
    fun `untilMonth before first transaction month does not shorten the range`() {
        val tx = tx(TransactionType.INCOME, 10_000, at = at("2026-03-05T00:00:00Z"))
        val result = snapshots(listOf(tx), "2025-12")
        assertEquals(listOf("2026-03"), result.map { it.monthYear })
        assertEquals(1_000_000L, result.single().openingBalance)
    }

    @Test
    fun `transfer destination gets amount without the fee`() {
        val tx = tx(TransactionType.TRANSFER, 100_000, fee = 3_000, to = B, at = at("2026-01-05T00:00:00Z"))
        val jan = BalanceCalculator.monthlySnapshots(B, 0, listOf(tx), utc, YearMonth.parse("2026-01")).single()
        assertEquals(100_000L, jan.totalTransferIn)
        assertEquals(0L, jan.totalExpense)
        assertEquals(0L, jan.totalTransferOut)
        assertEquals(100_000L, jan.closingBalance)
    }

    @Test
    fun `range of exactly the month cap still runs`() {
        val tx = tx(TransactionType.INCOME, 10_000, at = at("2026-01-05T00:00:00Z"))
        val until = YearMonth.parse("2026-01").plusMonths(BalanceCalculator.MAX_SNAPSHOT_MONTHS - 1L)
        val result = BalanceCalculator.monthlySnapshots(A, 1_000_000, listOf(tx), utc, until)
        assertEquals(BalanceCalculator.MAX_SNAPSHOT_MONTHS, result.size)
        assertEquals(until.toString(), result.last().monthYear)
    }

    @Test
    fun `range over the month cap throws`() {
        val tx = tx(TransactionType.INCOME, 10_000, at = at("2026-01-05T00:00:00Z"))
        val until = YearMonth.parse("2026-01").plusMonths(BalanceCalculator.MAX_SNAPSHOT_MONTHS.toLong())
        assertThrows(IllegalArgumentException::class.java) {
            BalanceCalculator.monthlySnapshots(A, 1_000_000, listOf(tx), utc, until)
        }
    }
}
