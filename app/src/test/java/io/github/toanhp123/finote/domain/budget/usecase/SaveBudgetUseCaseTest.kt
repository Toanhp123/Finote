package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.account.tx
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.FakeNotificationPromptRepository
import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.model.BudgetDraft
import io.github.toanhp123.finote.domain.budget.model.SaveBudgetResult
import io.github.toanhp123.finote.domain.budget.service.BudgetRules
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.model.Transaction
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
import java.time.ZoneOffset

class SaveBudgetUseCaseTest {
    private val oct = YearMonth.of(2026, 10)
    private val nov = YearMonth.of(2026, 11)
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC)
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val transactions = FakeTransactionRepository()
    private val prompt = FakeNotificationPromptRepository()
    private val runner = FakeTransactionRunner()
    private val ensure = EnsureBudgetMonthUseCase(budgets, runner, clock)
    private val calculate =
        CalculateBudgetStatusUseCase(
            budgets,
            ensure,
            ObserveReportInputUseCase(transactions, FakeAccountRepository(), categories, clock),
            clock,
        )
    private val save = SaveBudgetUseCase(budgets, categories, calculate, ensure, prompt, runner, clock)

    private fun draft(
        categoryId: String? = "food",
        limit: Long = 1_000_000,
        threshold: Int = 80,
        id: String? = null,
        isRollover: Boolean = false,
    ) = BudgetDraft(id, categoryId, limit, isRollover, threshold)

    private fun givenCategories() {
        categories.stored["food"] = testCategory(id = "food")
        categories.stored["pho"] = testCategory(id = "pho", parentId = "food")
        categories.stored["pay"] = testCategory(id = "pay", type = CategoryType.INCOME)
    }

    private fun spend(
        id: String,
        amount: Long,
        at: String,
    ): Transaction =
        tx(TransactionType.EXPENSE, amount, at = Instant.parse(at).toEpochMilli()).copy(id = id, categoryId = "food")

    private fun assertErr(
        expected: BudgetError,
        result: Outcome<SaveBudgetResult, BudgetError>,
    ) = assertEquals(Outcome.Err(expected), result)

    @Test
    fun `limit zero and above cap are InvalidLimit`() =
        runTest {
            givenCategories()

            listOf(0L, -1L, BudgetRules.MAX_LIMIT + 1, Long.MAX_VALUE).forEach {
                assertErr(BudgetError.InvalidLimit, save(draft(limit = it)))
            }
            assertTrue(budgets.stored.isEmpty())
        }

    @Test
    fun `the cap itself is saved`() =
        runTest {
            givenCategories()

            assertTrue(save(draft(limit = BudgetRules.MAX_LIMIT)) is Outcome.Ok)
        }

    @Test
    fun `threshold 60 is InvalidThreshold`() =
        runTest {
            givenCategories()

            assertErr(BudgetError.InvalidThreshold, save(draft(threshold = 60)))
            assertTrue(budgets.stored.isEmpty())
        }

    @Test
    fun `subcategory and income category are InvalidCategory`() =
        runTest {
            givenCategories()
            categories.stored["gone"] = testCategory(id = "gone").copy(deletedAt = 5L)

            listOf("pho", "pay", "gone", "missing").forEach {
                assertErr(BudgetError.InvalidCategory, save(draft(categoryId = it)))
            }
            assertTrue(budgets.stored.isEmpty())
        }

    @Test
    fun `second total budget in the month is Duplicate`() =
        runTest {
            givenCategories()
            assertTrue(save(draft(categoryId = null)) is Outcome.Ok)

            assertErr(BudgetError.Duplicate, save(draft(categoryId = null)))
            assertTrue(save(draft(categoryId = "food")) is Outcome.Ok)
            assertErr(BudgetError.Duplicate, save(draft(categoryId = "food")))
            assertEquals(2, budgets.getMonth(nov).size)
        }

    @Test
    fun `editing a past month row is NotCurrentMonth`() =
        runTest {
            givenCategories()
            budgets.stored["old"] = testBudget("old", oct, "food")
            budgets.setUp += oct

            assertErr(BudgetError.NotCurrentMonth, save(draft(id = "old")))
            assertEquals(1_000_000L, budgets.get("old")?.limitAmount)
        }

    @Test
    fun `editing an unknown id is NotFound`() =
        runTest {
            givenCategories()

            assertErr(BudgetError.NotFound, save(draft(id = "missing")))
        }

    @Test
    fun `an edit cannot change the category`() =
        runTest {
            givenCategories()
            categories.stored["fun"] = testCategory(id = "fun")
            budgets.stored["b"] = testBudget("b", nov, "food")
            budgets.setUp += nov

            assertErr(BudgetError.InvalidCategory, save(draft(id = "b", categoryId = "fun")))
            assertErr(BudgetError.InvalidCategory, save(draft(id = "b", categoryId = null)))
            assertEquals("food", budgets.get("b")?.categoryId)
        }

    @Test
    fun `saving a budget already at 95 percent stores mark 80 and the month is set up`() =
        runTest {
            givenCategories()
            transactions.transactionsFlow.value = listOf(spend("t1", 950_000, "2026-11-10T05:00:00Z"))

            assertTrue(save(draft()) is Outcome.Ok)

            assertEquals(80, budgets.getMonth(nov).single().lastAlertSentThreshold)
            assertTrue(budgets.isSetUp(nov))
        }

    @Test
    fun `an edit stores the mark its new values reach and keeps id and createdAt`() =
        runTest {
            givenCategories()
            budgets.stored["b"] = testBudget("b", nov, "food", 1_000_000, mark = 80).copy(createdAt = 7L)
            budgets.setUp += nov
            transactions.transactionsFlow.value = listOf(spend("t1", 950_000, "2026-11-10T05:00:00Z"))

            save(draft(id = "b", limit = 900_000)) // used up: mark 100
            assertEquals(100, budgets.get("b")?.lastAlertSentThreshold)
            save(draft(id = "b", limit = 5_000_000)) // far below the threshold: the mark clears
            val edited = budgets.get("b")

            assertNull(edited?.lastAlertSentThreshold)
            assertEquals(5_000_000L, edited?.limitAmount)
            assertEquals(7L, edited?.createdAt)
            assertEquals(clock.millis(), edited?.updatedAt)
            assertEquals(1, budgets.getMonth(nov).size)
        }

    @Test
    fun `the stored mark counts the carry from last month`() =
        runTest {
            givenCategories()
            budgets.stored["o"] = testBudget("o", oct, "food", 1_000_000, isRollover = true)
            budgets.setUp += oct
            ensure(nov)
            val copy = budgets.getMonth(nov).single()
            transactions.transactionsFlow.value =
                listOf(
                    spend("t-oct", 1_500_000, "2026-10-10T05:00:00Z"),
                    spend("t-nov", 450_000, "2026-11-10T05:00:00Z"),
                )

            // Available is 1M - 500k = 500k, so 450k spent is 90%: past the threshold of 80.
            save(draft(id = copy.id, isRollover = true))

            assertEquals(80, budgets.get(copy.id)?.lastAlertSentThreshold)
        }

    @Test
    fun `first save of a new month keeps last month's other budgets`() =
        runTest {
            givenCategories()
            categories.stored["fun"] = testCategory(id = "fun")
            budgets.stored["a"] = testBudget("a", oct, "food")
            budgets.stored["b"] = testBudget("b", oct, "fun")
            budgets.setUp += oct

            assertTrue(save(draft(categoryId = null)) is Outcome.Ok)

            assertEquals(3, budgets.getMonth(nov).size)
            assertEquals(setOf("food", "fun", null), budgets.getMonth(nov).map { it.categoryId }.toSet())
        }

    @Test
    fun `first save asks for the permission once`() =
        runTest {
            givenCategories()

            assertEquals(Outcome.Ok(SaveBudgetResult(true)), save(draft(categoryId = null)))
            assertEquals(Outcome.Ok(SaveBudgetResult(false)), save(draft(categoryId = "food")))
            assertTrue(prompt.asked)
        }

    @Test
    fun `a refused save does not use up the permission ask`() =
        runTest {
            givenCategories()

            save(draft(limit = 0))

            assertFalse(prompt.asked)
        }
}
