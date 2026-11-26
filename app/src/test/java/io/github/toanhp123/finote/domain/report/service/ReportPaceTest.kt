package io.github.toanhp123.finote.domain.report.service

import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class ReportPaceTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")

    private fun row(
        type: TransactionType,
        amount: Long,
        date: String,
    ) = tx(type, amount, at = noonOn(date))

    private fun noonOn(date: String) =
        LocalDate
            .parse(date)
            .atTime(12, 0)
            .atZone(vn)
            .toInstant()
            .toEpochMilli()

    private fun pace(
        month: String,
        today: String,
        vararg rows: Pair<TransactionType, Pair<Long, String>>,
    ) = ReportCalculator
        .monthReport(
            ReportInput(rows.map { (type, v) -> row(type, v.first, v.second) }, emptySet(), emptyList(), vn),
            YearMonth.parse(month),
            LocalDate.parse(today),
        ).pace

    private fun spend(
        amount: Long,
        date: String,
    ) = TransactionType.EXPENSE to (amount to date)

    @Test
    fun `current month compares the same days`() {
        val p =
            pace(
                "2026-11",
                "2026-11-10",
                spend(50_000, "2026-10-05"),
                spend(900_000, "2026-10-20"),
                spend(56_000, "2026-11-03"),
            )
        assertEquals(10, p.thisMonth.size)
        assertEquals(10, p.lastMonth.size)
        assertEquals(50_000L, p.lastMonth.last())
        assertEquals(56_000L, p.thisMonth.last())
        assertEquals(0L, p.thisMonth[1])
        assertEquals(12, p.percent)
    }

    @Test
    fun `previous month shorter stops at its last day`() {
        val p = pace("2026-03", "2026-03-31", spend(10_000, "2026-02-05"), spend(20_000, "2026-03-05"))
        assertEquals(31, p.thisMonth.size)
        assertEquals(28, p.lastMonth.size)
    }

    @Test
    fun `past month compares whole months`() {
        val p = pace("2026-10", "2026-11-25", spend(10_000, "2026-09-05"), spend(20_000, "2026-10-31"))
        assertEquals(31, p.thisMonth.size)
        assertEquals(30, p.lastMonth.size)
        assertEquals(100, p.percent)
    }

    @Test
    fun `last month with income only gives no baseline`() {
        val p =
            pace(
                "2026-11",
                "2026-11-10",
                TransactionType.INCOME to (500_000L to "2026-10-05"),
                spend(56_000, "2026-11-03"),
            )
        assertEquals(emptyList<Long>(), p.lastMonth)
        assertNull(p.percent)
    }

    @Test
    fun `rounding goes half up away from zero`() {
        fun percent(thisTotal: Long) =
            pace("2026-11", "2026-11-10", spend(100_000, "2026-10-05"), spend(thisTotal, "2026-11-03")).percent
        assertEquals(0, percent(100_400))
        assertEquals(1, percent(100_600))
        assertEquals(-1, percent(99_500))
    }

    @Test
    fun `last total at or below zero gives no percent`() {
        val p =
            pace(
                "2026-11",
                "2026-11-10",
                spend(10_000, "2026-10-05"),
                TransactionType.REFUND to (50_000L to "2026-10-06"),
                spend(56_000, "2026-11-03"),
            )
        assertEquals(10, p.lastMonth.size)
        assertNull(p.percent)
    }

    @Test
    fun `absurd ratio gives no percent`() {
        val p = pace("2026-11", "2026-11-10", spend(1, "2026-10-05"), spend(50_000_000_000, "2026-11-03"))
        assertNull(p.percent)
    }

    @Test
    fun `spending only after the cutoff gives a zero baseline`() {
        val p = pace("2026-11", "2026-11-10", spend(900_000, "2026-10-20"), spend(56_000, "2026-11-03"))
        assertEquals(List(10) { 0L }, p.lastMonth)
        assertNull(p.percent)
    }

    @Test
    fun `fee and refund count in the curve`() {
        val report =
            ReportCalculator.monthReport(
                ReportInput(
                    listOf(
                        row(TransactionType.EXPENSE, 100_000, "2026-11-02"),
                        tx(TransactionType.TRANSFER, 500_000, fee = 3_000, to = B, at = noonOn("2026-11-04")),
                        row(TransactionType.REFUND, 20_000, "2026-11-06"),
                    ),
                    emptySet(),
                    emptyList(),
                    vn,
                ),
                YearMonth.of(2026, 11),
                LocalDate.of(2026, 11, 10),
            )
        assertEquals(83_000L, report.expense)
        assertEquals(listOf(0L, 100_000L, 100_000L, 103_000L, 103_000L, 83_000L), report.pace.thisMonth.take(6))
        assertEquals(report.expense, report.pace.thisMonth.last())
    }

    @Test
    fun `future row is in the totals but not in the pace`() {
        val report =
            ReportCalculator.monthReport(
                ReportInput(
                    listOf(
                        row(TransactionType.EXPENSE, 10_000, "2026-11-03"),
                        row(TransactionType.EXPENSE, 7_000, "2026-11-28"),
                    ),
                    emptySet(),
                    emptyList(),
                    vn,
                ),
                YearMonth.of(2026, 11),
                LocalDate.of(2026, 11, 25),
            )
        assertEquals(17_000L, report.expense)
        assertEquals(25, report.pace.thisMonth.size)
        assertEquals(10_000L, report.pace.thisMonth.last())
    }
}
