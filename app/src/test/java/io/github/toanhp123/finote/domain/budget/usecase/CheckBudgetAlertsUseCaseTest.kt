package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetNotifier
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.FakeNotificationPromptRepository
import io.github.toanhp123.finote.domain.budget.model.Budget
import io.github.toanhp123.finote.domain.budget.model.BudgetDraft
import io.github.toanhp123.finote.domain.budget.repository.BudgetRepository
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

class CheckBudgetAlertsUseCaseTest {
    private val nov = YearMonth.of(2026, 11)
    private val dec = YearMonth.of(2026, 12)
    private val clock = MutableClock(Instant.parse("2026-11-15T05:00:00Z"))
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val notifier = FakeBudgetNotifier()
    private val runner = FakeTransactionRunner()
    private val input = ObserveReportInputUseCase(transactions, FakeAccountRepository(), categories, clock)

    private fun checkWith(repository: BudgetRepository): CheckBudgetAlertsUseCase {
        val ensure = EnsureBudgetMonthUseCase(repository, runner, clock)
        val observe = ObserveBudgetMonthUseCase(repository, ensure, input, clock)
        return CheckBudgetAlertsUseCase(repository, categories, observe, notifier, runner, clock)
    }

    private val check = checkWith(budgets)

    private fun givenFoodBudget(month: YearMonth = nov) {
        categories.stored["food"] = testCategory(id = "food", name = "Food")
        budgets.stored["b"] = testBudget("b", month, "food", limit = 1_000_000)
        budgets.setUp += month
    }

    private fun spend(
        id: String,
        amount: Long,
        at: String = "2026-11-10T05:00:00Z",
    ) {
        val row =
            tx(TransactionType.EXPENSE, amount, at = Instant.parse(at).toEpochMilli())
                .copy(id = id, categoryId = "food")
        transactions.transactionsFlow.value += row
    }

    private fun marks() = notifier.alerts.map { it.mark }

    @Test
    fun `no budgets reads nothing`() =
        runTest {
            val reads = mutableListOf<String>()
            val counting =
                object : BudgetRepository by budgets {
                    override suspend fun hasAny(): Boolean = budgets.hasAny().also { reads += "hasAny" }

                    override suspend fun getMonth(month: YearMonth): List<Budget> =
                        budgets.getMonth(month).also { reads += "getMonth" }

                    override suspend fun isSetUp(month: YearMonth): Boolean =
                        budgets.isSetUp(month).also { reads += "isSetUp" }
                }

            checkWith(counting)()

            assertEquals(listOf("hasAny"), reads)
            assertTrue(notifier.alerts.isEmpty())
        }

    @Test
    fun `crossing the threshold notifies once`() =
        runTest {
            givenFoodBudget()
            spend("t1", 800_000)

            check()
            check()

            assertEquals(listOf(80), marks())
            assertEquals(80, budgets.stored.getValue("b").lastAlertSentThreshold)
        }

    @Test
    fun `crossing 100 after 80 notifies 100`() =
        runTest {
            givenFoodBudget()
            spend("t1", 800_000)
            check()
            spend("t2", 300_000)

            check()

            assertEquals(listOf(80, 100), marks())
        }

    @Test
    fun `crossing both at once notifies only 100`() =
        runTest {
            givenFoodBudget()
            spend("t1", 1_100_000)

            check()

            assertEquals(listOf(100), marks())
        }

    @Test
    fun `no alert right after saving an over budget`() =
        runTest {
            categories.stored["food"] = testCategory(id = "food")
            spend("t1", 1_500_000)
            val ensure = EnsureBudgetMonthUseCase(budgets, runner, clock)
            val save =
                SaveBudgetUseCase(
                    budgets,
                    categories,
                    CalculateBudgetStatusUseCase(budgets, ensure, input, clock),
                    ensure,
                    FakeNotificationPromptRepository(),
                    runner,
                    clock,
                )
            save(BudgetDraft(null, "food", 1_000_000, isRollover = false, alertThreshold = 80))

            check()

            assertTrue(notifier.alerts.isEmpty())
        }

    @Test
    fun `a limit raised between read and write sends nothing`() =
        runTest {
            givenFoodBudget()
            spend("t1", 900_000)
            var isRaised = false
            val racing =
                object : BudgetRepository by budgets {
                    override suspend fun get(id: String): Budget? {
                        if (!isRaised) {
                            isRaised = true
                            budgets.stored[id] = budgets.stored.getValue(id).copy(limitAmount = 5_000_000)
                        }
                        return budgets.get(id)
                    }
                }

            checkWith(racing)()

            assertTrue(notifier.alerts.isEmpty())
            assertNull(budgets.stored.getValue("b").lastAlertSentThreshold)
        }

    @Test
    fun `no alert for last month after the month changes`() =
        runTest {
            givenFoodBudget()
            spend("t1", 900_000)
            clock.now = Instant.parse("2026-12-01T00:30:00Z")

            check()

            assertTrue(notifier.alerts.isEmpty())
            assertNull(budgets.stored.getValue("b").lastAlertSentThreshold)
        }

    @Test
    fun `after midnight at month end the new month is copied and alerts`() =
        runTest {
            givenFoodBudget()
            spend("t1", 900_000, at = "2026-12-01T00:10:00Z")
            clock.now = Instant.parse("2026-11-30T23:50:00Z")
            check()
            assertFalse(budgets.isSetUp(dec))
            clock.now = Instant.parse("2026-12-01T00:30:00Z")

            check()

            assertEquals(listOf(dec), notifier.alerts.map { it.month })
            assertEquals(listOf(80), marks())
            assertEquals(1, budgets.getMonth(dec).size)
        }

    @Test
    fun `alert carries the category nameKey and its toString has no name`() =
        runTest {
            categories.stored["food"] = testCategory(id = "food", name = "Secret name").copy(nameKey = "cat_food")
            budgets.stored["b"] = testBudget("b", nov, "food", limit = 1_000_000)
            budgets.setUp += nov
            spend("t1", 900_000)

            check()

            val alert = notifier.alerts.single()
            assertEquals("cat_food", alert.categoryNameKey)
            assertEquals("food", alert.categoryId)
            assertFalse(alert.toString().contains("Secret"))
        }
}

private class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
