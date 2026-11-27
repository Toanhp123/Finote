package io.github.toanhp123.finote.domain.budget.service

import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetState
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.Pace
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.YearMonth

class BudgetMathTest {
    private val sep = YearMonth.of(2026, 9)
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)

    private fun budget(
        limit: Long = 1_000_000,
        month: YearMonth = nov,
        category: String? = null,
        isRollover: Boolean = false,
        threshold: Int = 80,
        mark: Int? = null,
    ) = Budget("b-$month-$category", category, limit, month, isRollover, threshold, mark, 0, 0)

    private fun report(
        expense: Long,
        month: YearMonth = nov,
        vararg buckets: BucketTotal,
    ) = MonthReport(
        month,
        0,
        expense,
        -expense,
        buckets.toList(),
        emptyList(),
        emptyMap(),
        Pace(null, emptyList(), emptyList()),
        false,
    )

    private fun status(
        spent: Long,
        limit: Long = 1_000_000,
        carry: Long = 0,
        threshold: Int = 80,
        mark: Int? = null,
    ) = BudgetMath.status(budget(limit = limit, threshold = threshold, mark = mark), spent, carry)

    @Test
    fun `total spent is the month expense`() {
        assertEquals(700_000L, BudgetMath.spentOf(budget(), report(700_000)))
    }

    @Test
    fun `category spent is its bucket and 0 when absent`() {
        val r = report(900_000, nov, BucketTotal(ReportFilter.ByCategory("food"), 300_000))
        assertEquals(300_000L, BudgetMath.spentOf(budget(category = "food"), r))
        assertEquals(0L, BudgetMath.spentOf(budget(category = "gas"), r))
    }

    @Test
    fun `79_6 percent is normal and shows 79`() {
        val s = status(796_000)
        assertEquals(BudgetState.NORMAL, s.state)
        assertEquals(79, s.percent)
    }

    @Test
    fun `exactly the threshold is warning`() {
        val s = status(800_000)
        assertEquals(BudgetState.WARNING, s.state)
        assertEquals(80, s.percent)
    }

    @Test
    fun `exactly the limit is over and shows 100`() {
        val s = status(1_000_000)
        assertEquals(BudgetState.OVER, s.state)
        assertEquals(100, s.percent)
        assertEquals(0L, s.remaining)
    }

    @Test
    fun `over the limit is over with negative remaining`() {
        val s = status(1_001_000)
        assertEquals(BudgetState.OVER, s.state)
        assertEquals(100, s.percent)
        assertEquals(-1_000L, s.remaining)
    }

    @Test
    fun `negative spent shows 0 percent`() {
        val s = status(-50_000)
        assertEquals(BudgetState.NORMAL, s.state)
        assertEquals(0, s.percent)
    }

    @Test
    fun `available below zero with no spending is over with no percent and no alert`() {
        val s = status(spent = 0, limit = 100, carry = -500)
        assertEquals(BudgetState.OVER, s.state)
        assertNull(s.percent)
        assertNull(BudgetMath.alertToSend(s))
        assertNull(BudgetMath.reachedMark(s))
    }

    @Test
    fun `available below zero with spending still alerts 100`() {
        val s = status(spent = 50_000, limit = 100, carry = -500)
        assertEquals(BudgetState.OVER, s.state)
        assertNull(s.percent)
        assertEquals(100, BudgetMath.alertToSend(s))
        assertEquals(100, BudgetMath.reachedMark(s))
    }

    @Test
    fun `a huge overspend clamps the percent`() {
        assertEquals(Int.MAX_VALUE, status(spent = 30_000_000, limit = 1).percent)
    }

    @Test
    fun `carry adds last month leftover when rollover is on`() {
        val rows = listOf(budget(month = oct), budget(month = nov, isRollover = true))
        val s = BudgetMath.statuses(nov, rows, mapOf(oct to report(600_000, oct))).single()
        assertEquals(400_000L, s.carry)
        assertEquals(1_400_000L, s.available)
    }

    @Test
    fun `carry chains two months`() {
        val rows =
            listOf(
                budget(month = sep),
                budget(month = oct, isRollover = true),
                budget(month = nov, isRollover = true),
            )
        val reports = mapOf(sep to report(900_000, sep), oct to report(1_050_000, oct))
        val s = BudgetMath.statuses(nov, rows, reports).single()
        assertEquals(50_000L, s.carry)
    }

    @Test
    fun `a month missing from reports counts as spent 0`() {
        val rows = listOf(budget(month = oct), budget(month = nov, isRollover = true))
        assertEquals(1_000_000L, BudgetMath.statuses(nov, rows, emptyMap()).single().carry)
    }

    @Test
    fun `no carry when rollover is off or last month has no row`() {
        val reports = mapOf(oct to report(600_000, oct))
        val off = listOf(budget(month = oct), budget(month = nov))
        assertEquals(0L, BudgetMath.statuses(nov, off, reports).single().carry)
        val noRow = listOf(budget(month = oct, category = "food"), budget(month = nov, isRollover = true))
        assertEquals(0L, BudgetMath.statuses(nov, noRow, reports).single().carry)
    }

    @Test
    fun `carry follows the same category only`() {
        val rows =
            listOf(
                budget(month = oct, category = "food"),
                budget(month = oct),
                budget(month = nov, category = "food", isRollover = true),
            )
        val reports =
            mapOf(oct to report(900_000, oct, BucketTotal(ReportFilter.ByCategory("food"), 100_000)))
        assertEquals(900_000L, BudgetMath.statuses(nov, rows, reports).single().carry)
    }

    @Test
    fun `alertToSend sends 100 once`() {
        assertEquals(100, BudgetMath.alertToSend(status(1_200_000, mark = null)))
        assertNull(BudgetMath.alertToSend(status(1_200_000, mark = 100)))
    }

    @Test
    fun `crossing both marks sends only 100`() {
        assertEquals(100, BudgetMath.alertToSend(status(1_000_000, mark = null)))
        assertEquals(100, BudgetMath.alertToSend(status(1_000_000, mark = 80)))
    }

    @Test
    fun `warning sends the threshold once`() {
        assertEquals(80, BudgetMath.alertToSend(status(850_000, mark = null)))
        assertNull(BudgetMath.alertToSend(status(850_000, mark = 80)))
    }

    @Test
    fun `a lower mark than sent never alerts`() {
        assertNull(BudgetMath.alertToSend(status(600_000, threshold = 50, mark = 70)))
    }

    @Test
    fun `reachedMark after a raised limit drops to the threshold`() {
        assertEquals(80, BudgetMath.reachedMark(status(900_000, mark = 100)))
    }

    @Test
    fun `reachedMark is null while normal and for an exactly spent zero limit month`() {
        assertNull(BudgetMath.reachedMark(status(100_000)))
        assertNull(BudgetMath.reachedMark(status(0)))
    }

    @Test
    fun `toString hides money`() {
        val s: BudgetStatus = status(123_456)
        assertEquals(false, s.toString().contains("123456"))
        assertEquals(false, s.budget.toString().contains("1000000"))
    }
}
