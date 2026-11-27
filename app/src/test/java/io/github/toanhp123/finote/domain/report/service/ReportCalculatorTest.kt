package io.github.toanhp123.finote.domain.report.service

import io.github.toanhp123.finote.domain.account.A
import io.github.toanhp123.finote.domain.account.B
import io.github.toanhp123.finote.domain.account.C
import io.github.toanhp123.finote.domain.account.adjustment
import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.report.model.BucketTotal
import io.github.toanhp123.finote.domain.report.model.MerchantTotal
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.model.ReportInput
import io.github.toanhp123.finote.domain.report.model.ReportRow
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId

class ReportCalculatorTest {
    private val vn = ZoneId.of("Asia/Ho_Chi_Minh")
    private val month = YearMonth.of(2026, 11)
    private val today = LocalDate.of(2026, 11, 25)
    private val noon = ms("2026-11-15T05:00:00Z")
    private var nextId = 0

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun row(
        type: TransactionType,
        amount: Long,
        category: String? = null,
        merchant: String? = null,
        fee: Long = 0,
        at: Long = noon,
    ): Transaction =
        // A transfer always goes from A to B.
        tx(type, amount, fee = fee, to = if (type == TransactionType.TRANSFER) B else null, at = at)
            .copy(id = "t${nextId++}", categoryId = category, merchantName = merchant)

    private fun category(
        id: String,
        parent: String? = null,
    ) = Category(id, id, null, "", 0, CategoryType.EXPENSE, parent, false, false, 0, 0, 0, null)

    private fun input(
        vararg rows: Transaction,
        categories: List<Category> = emptyList(),
        ignored: Set<String> = emptySet(),
    ) = ReportInput(rows.toList(), ignored, categories, vn)

    private fun report(input: ReportInput) = ReportCalculator.monthReport(input, month, today)

    private fun cat(id: String) = ReportFilter.ByCategory(id)

    @Test
    fun `income and expense add up and net is income minus expense`() {
        val r =
            report(
                input(
                    row(TransactionType.INCOME, 1_000_000),
                    row(TransactionType.EXPENSE, 300_000),
                ),
            )
        assertEquals(1_000_000L, r.income)
        assertEquals(300_000L, r.expense)
        assertEquals(700_000L, r.net)
    }

    @Test
    fun `transfer adds only its fee to the fee bucket`() {
        val r = report(input(row(TransactionType.TRANSFER, 2_000_000, fee = 5_000)))
        assertEquals(0L, r.income)
        assertEquals(5_000L, r.expense)
        assertEquals(listOf(BucketTotal(ReportFilter.TransferFees, 5_000)), r.buckets)
    }

    @Test
    fun `adjustment adds nothing`() {
        val r =
            report(
                input(
                    adjustment(AdjustmentDirection.IN, 50_000, at = noon),
                    adjustment(AdjustmentDirection.OUT, 70_000, at = noon),
                ),
            )
        assertEquals(0L, r.income)
        assertEquals(0L, r.expense)
        assertEquals(emptyList<BucketTotal>(), r.buckets)
    }

    @Test
    fun `refund lowers its bucket and the expense, never income`() {
        val r =
            report(
                input(
                    row(TransactionType.EXPENSE, 200_000, category = "a"),
                    row(TransactionType.REFUND, 50_000, category = "a"),
                    categories = listOf(category("a")),
                ),
            )
        assertEquals(0L, r.income)
        assertEquals(150_000L, r.expense)
        assertEquals(listOf(BucketTotal(cat("a"), 150_000)), r.buckets)
    }

    @Test
    fun `excluded row is skipped with its fee`() {
        val excluded =
            row(TransactionType.TRANSFER, 1_000_000, fee = 3_000).copy(isExcludedFromAnalytics = true)
        val r = report(input(excluded, row(TransactionType.EXPENSE, 10_000).copy(isExcludedFromAnalytics = true)))
        assertEquals(0L, r.expense)
        assertEquals(emptyList<BucketTotal>(), r.buckets)
    }

    @Test
    fun `deleted row is skipped`() {
        val r = report(input(row(TransactionType.EXPENSE, 10_000).copy(deletedAt = 1)))
        assertEquals(0L, r.expense)
    }

    @Test
    fun `row of an ignored account is skipped, archived account still counts`() {
        val r =
            report(
                input(
                    row(TransactionType.EXPENSE, 100_000).copy(accountId = B),
                    row(TransactionType.EXPENSE, 20_000).copy(accountId = C),
                    ignored = setOf(B),
                ),
            )
        assertEquals(20_000L, r.expense)
    }

    @Test
    fun `child rolls into its parent and expenseByCategory keeps the leaf`() {
        val r =
            report(
                input(
                    row(TransactionType.EXPENSE, 100_000, category = "c1"),
                    row(TransactionType.EXPENSE, 50_000, category = "p"),
                    categories = listOf(category("p"), category("c1", parent = "p")),
                ),
            )
        assertEquals(listOf(BucketTotal(cat("p"), 150_000)), r.buckets)
        assertEquals(mapOf("c1" to 100_000L, "p" to 50_000L), r.expenseByCategory)
    }

    @Test
    fun `missing category is uncategorized, an orphaned child is its own bucket`() {
        val r =
            report(
                input(
                    row(TransactionType.EXPENSE, 30_000, category = "gone"),
                    row(TransactionType.EXPENSE, 20_000),
                    row(TransactionType.EXPENSE, 10_000, category = "orphan"),
                    categories = listOf(category("orphan", parent = "lost")),
                ),
            )
        assertEquals(
            listOf(BucketTotal(ReportFilter.Uncategorized, 50_000), BucketTotal(cat("orphan"), 10_000)),
            r.buckets,
        )
        assertEquals(mapOf("orphan" to 10_000L), r.expenseByCategory)
    }

    @Test
    fun `negative bucket still shows and buckets add up to expense`() {
        val r =
            report(
                input(
                    row(TransactionType.REFUND, 80_000, category = "b"),
                    row(TransactionType.EXPENSE, 100_000, category = "a"),
                    categories = listOf(category("a"), category("b")),
                ),
            )
        assertEquals(listOf(BucketTotal(cat("a"), 100_000), BucketTotal(cat("b"), -80_000)), r.buckets)
        assertEquals(20_000L, r.expense)
        assertEquals(r.expense, r.buckets.sumOf { it.amount })
    }

    @Test
    fun `buckets tie-break by category id, then uncategorized, then fees`() {
        val r =
            report(
                input(
                    row(TransactionType.TRANSFER, 1_000_000, fee = 10_000),
                    row(TransactionType.EXPENSE, 10_000),
                    row(TransactionType.EXPENSE, 10_000, category = "z"),
                    row(TransactionType.EXPENSE, 10_000, category = "m"),
                    categories = listOf(category("z"), category("m")),
                ),
            )
        assertEquals(
            listOf(cat("m"), cat("z"), ReportFilter.Uncategorized, ReportFilter.TransferFees),
            r.buckets.map { it.filter },
        )
    }

    @Test
    fun `merchant top five skips blank names and totals at or below zero`() {
        val rows =
            listOf(
                row(TransactionType.EXPENSE, 100_000, merchant = "B"),
                row(TransactionType.EXPENSE, 100_000, merchant = "A"),
                row(TransactionType.EXPENSE, 300_000, merchant = "C"),
                row(TransactionType.EXPENSE, 50_000, merchant = "D"),
                row(TransactionType.EXPENSE, 40_000, merchant = "E"),
                row(TransactionType.EXPENSE, 30_000, merchant = "F"),
                row(TransactionType.EXPENSE, 999_000, merchant = "  "),
                row(TransactionType.EXPENSE, 999_000),
                row(TransactionType.INCOME, 999_000, merchant = "A"),
                row(TransactionType.EXPENSE, 20_000, merchant = "Zero"),
                row(TransactionType.REFUND, 20_000, merchant = "Zero"),
                row(TransactionType.REFUND, 5_000, merchant = "Neg"),
            )
        val r = report(ReportInput(rows, emptySet(), emptyList(), vn))
        assertEquals(
            listOf(
                MerchantTotal("C", 300_000),
                MerchantTotal("A", 100_000),
                MerchantTotal("B", 100_000),
                MerchantTotal("D", 50_000),
                MerchantTotal("E", 40_000),
            ),
            r.merchants,
        )
    }

    @Test
    fun `rows of a bucket total to the bucket amount`() {
        val rows =
            listOf(
                row(TransactionType.EXPENSE, 100_000, category = "c1", merchant = "Shop"),
                row(TransactionType.EXPENSE, 50_000, category = "p", merchant = "Shop"),
                row(TransactionType.REFUND, 30_000, category = "c1", merchant = "Shop"),
                row(TransactionType.EXPENSE, 20_000, merchant = "Cafe"),
                row(TransactionType.EXPENSE, 7_000, category = "gone"),
                row(TransactionType.TRANSFER, 1_000_000, fee = 3_300),
                row(TransactionType.EXPENSE, 9_000, fee = 1_000),
                row(TransactionType.EXPENSE, 5_000, category = "p").copy(isExcludedFromAnalytics = true),
                row(TransactionType.INCOME, 400_000, category = "p", merchant = "Shop"),
            )
        val input =
            ReportInput(rows, emptySet(), listOf(category("p"), category("c1", parent = "p")), vn)
        val r = report(input)
        assertEquals(3, r.buckets.size)
        for (b in r.buckets) {
            assertEquals("bucket ${b.filter}", b.amount, ReportCalculator.rows(input, month, b.filter).total)
        }
        for (m in listOf(MerchantTotal("Shop", 120_000), MerchantTotal("Cafe", 20_000))) {
            assertEquals(m.amount, ReportCalculator.rows(input, month, ReportFilter.ByMerchant(m.name)).total)
        }
        assertEquals(r.merchants.take(2), listOf(MerchantTotal("Shop", 120_000), MerchantTotal("Cafe", 20_000)))
    }

    @Test
    fun `all spending rows total the month expense`() {
        val input =
            input(
                row(TransactionType.EXPENSE, 100_000, category = "c1"),
                row(TransactionType.EXPENSE, 9_000, fee = 1_000),
                row(TransactionType.REFUND, 30_000, category = "c1"),
                row(TransactionType.TRANSFER, 1_000_000, fee = 3_300),
                row(TransactionType.INCOME, 400_000),
                row(TransactionType.EXPENSE, 5_000).copy(isExcludedFromAnalytics = true),
            )
        val rows = ReportCalculator.rows(input, month, ReportFilter.AllSpending)
        assertEquals(report(input).expense, rows.total)
        assertEquals(5, rows.rows.size)
        assertTrue(rows.rows.any { it.amount < 0 })
    }

    @Test
    fun `fee rows carry the fee as amount`() {
        val transfer = row(TransactionType.TRANSFER, 2_000_000, fee = 5_000)
        val rows = ReportCalculator.rows(input(transfer), month, ReportFilter.TransferFees)
        assertEquals(listOf(ReportRow(transfer, 5_000)), rows.rows)
        assertEquals(5_000L, rows.total)
    }

    @Test
    fun `rows are newest first, ties by createdAt then id`() {
        val old = row(TransactionType.EXPENSE, 1_000, at = noon - 1).copy(id = "z")
        val a = row(TransactionType.EXPENSE, 1_000).copy(id = "a", createdAt = 1)
        val b = row(TransactionType.EXPENSE, 1_000).copy(id = "b", createdAt = 1)
        val c = row(TransactionType.EXPENSE, 1_000).copy(id = "c", createdAt = 2)
        val rows = ReportCalculator.rows(input(old, a, b, c), month, ReportFilter.Uncategorized)
        assertEquals(listOf("c", "b", "a", "z"), rows.rows.map { it.transaction.id })
    }

    @Test
    fun `month boundary uses the zone`() {
        fun at(iso: String) = OffsetDateTime.parse(iso).toInstant().toEpochMilli()
        val oct = row(TransactionType.EXPENSE, 100_000, at = at("2026-10-31T23:59:59.999+07:00"))
        val nov = row(TransactionType.EXPENSE, 200_000, at = at("2026-11-01T00:00:00+07:00"))
        val input = input(oct, nov)
        assertEquals(200_000L, report(input).expense)
        assertEquals(100_000L, ReportCalculator.monthReport(input, YearMonth.of(2026, 10), today).expense)
    }

    @Test
    fun `future row in the current month sets hasFutureRows`() {
        val past = row(TransactionType.EXPENSE, 1_000, at = ms("2026-11-20T05:00:00Z"))
        val future = row(TransactionType.EXPENSE, 1_000, at = ms("2026-11-28T05:00:00Z"))
        assertFalse(report(input(past)).hasFutureRows)
        assertTrue(report(input(past, future)).hasFutureRows)
        val oct = ReportCalculator.monthReport(input(past, future), YearMonth.of(2026, 10), today)
        assertFalse(oct.hasFutureRows)
        val later = LocalDate.of(2026, 12, 5)
        assertFalse(ReportCalculator.monthReport(input(past, future), month, later).hasFutureRows)
    }

    @Test
    fun `overflow throws ArithmeticException`() {
        val big = Long.MAX_VALUE / 2 + 1
        val input = input(row(TransactionType.EXPENSE, big), row(TransactionType.EXPENSE, big))
        assertThrows(ArithmeticException::class.java) { report(input) }
    }

    @Test
    fun `toString hides money and names`() {
        val money = 7_654_321L
        val r =
            report(
                input(
                    row(TransactionType.EXPENSE, money, category = "a", merchant = "Highlands"),
                    row(TransactionType.INCOME, money),
                    categories = listOf(category("a")),
                ),
            )
        val parts =
            listOf(
                r.toString(),
                MerchantTotal("Highlands", money).toString(),
                ReportFilter.ByMerchant("Highlands").toString(),
                ReportRow(row(TransactionType.EXPENSE, money, merchant = "Highlands"), money).toString(),
                r.buckets.first().toString(),
                ReportCalculator
                    .rows(input(row(TransactionType.EXPENSE, money)), month, ReportFilter.Uncategorized)
                    .toString(),
                input(row(TransactionType.EXPENSE, money, merchant = "Highlands")).toString(),
            )
        for (s in parts) {
            assertFalse(s, s.contains("7654321") || s.contains("7_654_321") || s.contains("Highlands"))
        }
    }
}
