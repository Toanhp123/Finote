package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class ObserveBudgetMonthUseCaseTest {
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val clock = Clock.fixed(Instant.parse("2026-12-15T05:00:00Z"), ZoneOffset.UTC)
    private val budgets = FakeBudgetRepository()
    private val transactions = FakeTransactionRepository()
    private val categories = FakeCategoryRepository()
    private val ensure = EnsureBudgetMonthUseCase(budgets, FakeTransactionRunner(), clock)
    private val observe =
        ObserveBudgetMonthUseCase(
            budgets,
            ensure,
            ObserveReportInputUseCase(transactions, FakeAccountRepository(), categories, clock),
            clock,
        )

    private fun spend(
        id: String,
        category: String,
        amount: Long,
        at: String,
    ): Transaction =
        tx(TransactionType.EXPENSE, amount, at = Instant.parse(at).toEpochMilli()).copy(id = id, categoryId = category)

    private fun category(
        id: String,
        sortOrder: Int,
    ) {
        categories.stored[id] = testCategory(id = id).copy(sortOrder = sortOrder)
    }

    @Test
    fun `a skipped month carries through`() =
        runTest {
            category("food", 0)
            budgets.stored["a"] = testBudget("a", oct, "food", 1_000_000, isRollover = true)
            budgets.setUp += oct
            transactions.transactionsFlow.value =
                listOf(
                    spend("t-oct", "food", 400_000, "2026-10-10T05:00:00Z"),
                    spend("t-nov", "food", 300_000, "2026-11-10T05:00:00Z"),
                    spend("t-dec", "food", 100_000, "2026-12-10T05:00:00Z"),
                )

            val food = observe(dec).first().categories.single()

            assertEquals(1, budgets.getMonth(nov).size)
            // Nov carry is 1M - 400k = 600k; Dec carry is (1M + 600k) - 300k spent in the month nobody opened.
            assertEquals(1_300_000L, food.carry)
            assertEquals(2_300_000L, food.available)
            assertEquals(100_000L, food.spent)
        }

    @Test
    fun `a past month is not copied into`() =
        runTest {
            budgets.stored["a"] = testBudget("a", oct, "food")
            budgets.setUp += oct

            val result = observe(nov).first()

            assertTrue(budgets.getMonth(nov).isEmpty())
            assertTrue(result.categories.isEmpty())
            assertNull(result.total)
        }

    @Test
    fun `the total is apart and sort puts over first then by percent`() =
        runTest {
            listOf("a", "b", "c", "d", "e").forEachIndexed { index, id -> category(id, index) }
            // d: rollover debt leaves nothing available, c: 120%, b: 96%, a and e: 50%.
            budgets.stored["d0"] = testBudget("d0", nov, "d", 100, isRollover = true)
            budgets.stored["total"] = testBudget("total", dec, null, 10_000_000)
            budgets.stored["a"] = testBudget("a", dec, "a", 1000)
            budgets.stored["b"] = testBudget("b", dec, "b", 1000)
            budgets.stored["c"] = testBudget("c", dec, "c", 1000)
            budgets.stored["d"] = testBudget("d", dec, "d", 100, isRollover = true)
            budgets.stored["e"] = testBudget("e", dec, "e", 1000)
            budgets.setUp += nov
            budgets.setUp += dec
            val at = "2026-12-10T05:00:00Z"
            transactions.transactionsFlow.value =
                listOf(
                    spend("ta", "a", 500, at),
                    spend("tb", "b", 960, at),
                    spend("tc", "c", 1200, at),
                    spend("te", "e", 500, at),
                    spend("td-nov", "d", 500, "2026-11-10T05:00:00Z"),
                )

            val result = observe(dec).first()

            assertEquals("total", result.total?.budget?.id)
            // d: carry 100 - 500 = -400, so available is -300 and the percent is null. a before e by category order.
            assertEquals(listOf("d", "c", "b", "a", "e"), result.categories.map { it.budget.id })
        }
}
