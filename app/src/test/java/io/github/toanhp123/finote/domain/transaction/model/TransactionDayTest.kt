package io.github.toanhp123.finote.domain.transaction.model

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TransactionDayTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val noon = ms("2026-11-04T05:00:00Z")

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun netOf(
        vararg rows: Transaction,
        accountId: String? = null,
    ) = groupByDay(rows.toList(), vn, accountId).single().net

    @Test
    fun `empty list gives no days`() {
        assertEquals(emptyList<TransactionDay>(), groupByDay(emptyList(), vn, null))
    }

    @Test
    fun `keeps order and splits days at local midnight`() {
        val late = tx(TransactionType.EXPENSE, 1, at = ms("2026-11-04T17:30:00Z")).copy(id = "late")
        val early = tx(TransactionType.EXPENSE, 1, at = ms("2026-11-04T16:30:00Z")).copy(id = "early")
        val days = groupByDay(listOf(late, early), vn, null)
        assertEquals(listOf(LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 4)), days.map { it.date })
        assertEquals(listOf(listOf(late), listOf(early)), days.map { it.transactions })
    }

    @Test
    fun `same instant lands on different days in different zones`() {
        val row = tx(TransactionType.EXPENSE, 1, at = ms("2026-11-04T17:30:00Z"))
        assertEquals(LocalDate.of(2026, 11, 4), groupByDay(listOf(row), ZoneId.of("UTC"), null).single().date)
        assertEquals(LocalDate.of(2026, 11, 5), groupByDay(listOf(row), vn, null).single().date)
    }

    @Test
    fun `net adds income and refund, subtracts expense`() {
        val net =
            netOf(
                tx(TransactionType.INCOME, 500_000, at = noon),
                tx(TransactionType.REFUND, 20_000, at = noon),
                tx(TransactionType.EXPENSE, 120_000, at = noon),
            )
        assertEquals(400_000L, net)
    }

    @Test
    fun `fees count as spending for every type`() {
        val net =
            netOf(
                tx(TransactionType.EXPENSE, 100_000, fee = 1_000, at = noon),
                tx(TransactionType.TRANSFER, 1_000_000, fee = 3_300, to = B, at = noon),
            )
        assertEquals(-104_300L, net)
    }

    @Test
    fun `transfer principal and adjustment are left out`() {
        val transfer = tx(TransactionType.TRANSFER, 1_000_000, to = B, at = noon)
        val adjust = adjustment(AdjustmentDirection.IN, 50_000, at = noon)
        val day = groupByDay(listOf(transfer, adjust), vn, null).single()
        assertEquals(0L, day.net)
        assertEquals(listOf(transfer, adjust), day.transactions)
    }

    @Test
    fun `excluded rows stay listed but add nothing, fee included`() {
        val row = tx(TransactionType.EXPENSE, 200_000, fee = 2_000, at = noon).copy(isExcludedFromAnalytics = true)
        val day = groupByDay(listOf(row), vn, null).single()
        assertEquals(0L, day.net)
        assertEquals(1, day.transactions.size)
    }

    @Test
    fun `fee of a transfer is skipped when filtering by its destination`() {
        val transfer = tx(TransactionType.TRANSFER, 1_000_000, fee = 3_300, account = A, to = B, at = noon)
        assertEquals(0L, netOf(transfer, accountId = B))
        assertEquals(-3_300L, netOf(transfer, accountId = A))
    }

    @Test
    fun `overflow throws`() {
        assertThrows(ArithmeticException::class.java) {
            netOf(
                tx(TransactionType.INCOME, Long.MAX_VALUE, at = noon).copy(id = "a"),
                tx(TransactionType.INCOME, Long.MAX_VALUE, at = noon).copy(id = "b"),
            )
        }
    }

    @Test
    fun `net of Long MIN_VALUE throws`() {
        assertThrows(ArithmeticException::class.java) {
            netOf(tx(TransactionType.EXPENSE, Long.MAX_VALUE, fee = 1, at = noon))
        }
    }
}
