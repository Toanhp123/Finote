package io.github.toanhp123.finote.ui.report.logic

import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.ui.navigation.ReportList
import io.github.toanhp123.finote.ui.navigation.ReportListKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.YearMonth

class ReportListKeyTest {
    private val month = YearMonth.of(2026, 11)

    @Test
    fun `every filter round trips through the nav key`() {
        val filters =
            listOf(
                ReportFilter.ByCategory("cat-1"),
                ReportFilter.Uncategorized,
                ReportFilter.TransferFees,
                ReportFilter.ByMerchant("Highlands"),
                ReportFilter.AllSpending,
            )

        filters.forEach { filter ->
            val key = filter.toNavKey(month)

            assertEquals(filter, key.toFilter())
            assertEquals(month, key.yearMonth())
        }
    }

    @Test
    fun `category key without id throws`() {
        assertThrows(IllegalArgumentException::class.java) { ReportList("2026-11", ReportListKind.CATEGORY).toFilter() }
        assertThrows(IllegalArgumentException::class.java) { ReportList("2026-11", ReportListKind.MERCHANT).toFilter() }
    }

    @Test
    fun `key on a kind that takes none throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            ReportList("2026-11", ReportListKind.UNCATEGORIZED, "x").toFilter()
        }
        assertThrows(IllegalArgumentException::class.java) {
            ReportList("2026-11", ReportListKind.TRANSFER_FEES, "x").toFilter()
        }
    }

    @Test
    fun `ALL_SPENDING key round trips and takes no key`() {
        val key = ReportFilter.AllSpending.toNavKey(month)

        assertEquals(ReportList("2026-11", ReportListKind.ALL_SPENDING), key)
        assertEquals(ReportFilter.AllSpending, key.toFilter())
        assertThrows(IllegalArgumentException::class.java) {
            ReportList("2026-11", ReportListKind.ALL_SPENDING, "x").toFilter()
        }
    }

    @Test
    fun `toString hides the key`() {
        val key = ReportFilter.ByMerchant("Highlands").toNavKey(month)

        assertFalse(key.toString().contains("Highlands"))
    }

    @Test
    fun `lists of one month and kind with another key have another content key`() {
        val food = ReportFilter.ByCategory("food").toNavKey(month)
        val rent = ReportFilter.ByCategory("rent").toNavKey(month)

        assertNotEquals(food.contentKey(), rent.contentKey())
        assertEquals(food.contentKey(), ReportFilter.ByCategory("food").toNavKey(month).contentKey())
        assertNotEquals(food.contentKey(), ReportFilter.ByCategory("food").toNavKey(month.plusMonths(1)).contentKey())
        assertNotEquals(
            ReportFilter.Uncategorized.toNavKey(month).contentKey(),
            ReportFilter.TransferFees.toNavKey(month).contentKey(),
        )
    }
}
