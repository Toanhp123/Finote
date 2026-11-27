package io.github.toanhp123.finote.ui.budgetedit

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.FakeNotificationPromptRepository
import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.budget.usecase.CalculateBudgetStatusUseCase
import io.github.toanhp123.finote.domain.budget.usecase.DeleteBudgetUseCase
import io.github.toanhp123.finote.domain.budget.usecase.EnsureBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.SaveBudgetUseCase
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetEditViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val november = YearMonth.of(2026, 11)
    private val clock = MutableClock(Instant.parse("2026-11-15T05:00:00Z"))
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()
    private val prompt = FakeNotificationPromptRepository()
    private val runner = FakeTransactionRunner()

    private fun TestScope.open(
        id: String? = null,
        isSettled: Boolean = true,
    ): BudgetEditViewModel {
        val ensure = EnsureBudgetMonthUseCase(budgets, runner, clock)
        val reportInput =
            ObserveReportInputUseCase(FakeTransactionRepository(), FakeAccountRepository(), categories, clock)
        val calculate = CalculateBudgetStatusUseCase(budgets, ensure, reportInput, clock)
        return BudgetEditViewModel(
            id = id,
            observeBudgetMonth = ObserveBudgetMonthUseCase(budgets, ensure, reportInput, clock),
            observeCategories = ObserveCategoriesUseCase(categories),
            saveBudget = SaveBudgetUseCase(budgets, categories, calculate, ensure, prompt, runner, clock),
            deleteBudget = DeleteBudgetUseCase(budgets, runner, clock),
            isDebuggable = true,
        ).also { if (isSettled) advanceUntilIdle() }
    }

    private fun givenCategories() {
        categories.stored["food"] = testCategory("food")
        categories.stored["shopping"] = testCategory("shopping")
        categories.stored["pay"] = testCategory("pay", type = CategoryType.INCOME)
        categories.stored["pho"] = testCategory("pho", parentId = "food")
        categories.stored["hidden"] = testCategory("hidden").copy(isHidden = true)
    }

    private fun setUp(vararg rows: Pair<String, String?>) {
        budgets.setUp += november
        rows.forEach { (id, category) -> budgets.stored[id] = testBudget(id, november, category) }
    }

    @Test
    fun `edit locks the category and loads the stored values`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            budgets.setUp += november
            budgets.stored["b1"] =
                testBudget("b1", november, "food", limit = 2_000_000, isRollover = true, threshold = 90)
            val vm = open("b1", isSettled = false)
            assertTrue(vm.state.value.isLoading)

            advanceUntilIdle()

            val state = vm.state.value
            assertFalse(state.isLoading)
            assertEquals("b1", state.id)
            assertEquals("food", state.categoryId)
            assertTrue(state.isCategoryLocked)
            assertEquals("2000000", state.limitText)
            assertTrue(state.isRollover)
            assertEquals(90, state.threshold)
            assertTrue(state.isVisible)
        }

    @Test
    fun `create offers only categories without a budget`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp("b1" to "food")

            val state = open().state.value

            // The total first, then shown top-level expense categories that are free: no income, child or hidden one.
            assertEquals(listOf(null, "shopping"), state.choices.map { it?.id })
            assertFalse(state.isCategoryLocked)
            assertNull(state.categoryId)

            budgets.stored["t"] = testBudget("t", november, null)
            val withTotal = open().state.value
            assertEquals(listOf("shopping"), withTotal.choices.map { it?.id })
            assertEquals("shopping", withTotal.categoryId)
        }

    @Test
    fun `save maps InvalidLimit to the field error and Duplicate to a message above Save`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp("b1" to "food")
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetEditIntent.LimitChanged("0"))
                vm.onIntent(BudgetEditIntent.SaveClicked)
                advanceUntilIdle()
                assertEquals(BudgetError.InvalidLimit, vm.state.value.limitError)
                assertFalse(vm.state.value.isSaving)
                expectNoEvents()

                // Typing clears the field error. A category that got its budget meanwhile is a duplicate.
                vm.onIntent(BudgetEditIntent.LimitChanged("5000"))
                assertNull(vm.state.value.limitError)
                vm.onIntent(BudgetEditIntent.CategoryPicked("food"))
                vm.onIntent(BudgetEditIntent.SaveClicked)
                advanceUntilIdle()
                // The message is state inside the sheet, not an effect: a snackbar would hide under the sheet.
                expectNoEvents()
            }
            assertEquals(BudgetEditError.DUPLICATE, vm.state.value.formError)
            assertNull(vm.state.value.limitError)
            assertFalse(vm.state.value.isSaving)
            assertTrue(vm.state.value.isVisible)

            // The next edit clears it.
            vm.onIntent(BudgetEditIntent.CategoryPicked("shopping"))
            assertNull(vm.state.value.formError)
        }

    @Test
    fun `a failed save shows the message until the next save`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp()
            val vm = open()
            vm.onIntent(BudgetEditIntent.CategoryPicked("food"))
            vm.onIntent(BudgetEditIntent.LimitChanged("1000"))
            // A pick of a category that is not an expense one is refused by the domain.
            vm.onIntent(BudgetEditIntent.CategoryPicked("pay"))
            vm.onIntent(BudgetEditIntent.SaveClicked)
            advanceUntilIdle()
            assertEquals(BudgetEditError.INVALID_CATEGORY, vm.state.value.formError)

            vm.onIntent(BudgetEditIntent.CategoryPicked("food"))
            prompt.asked = true
            vm.onIntent(BudgetEditIntent.SaveClicked)
            assertNull(vm.state.value.formError)
            advanceUntilIdle()
            assertFalse(vm.state.value.isVisible)
        }

    @Test
    fun `saving a row of an older month closes the sheet`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp("b1" to "food")
            val vm = open("b1")

            // The app stayed open past midnight at month end: the row is from the month before now.
            clock.now = Instant.parse("2026-12-01T05:00:00Z")
            vm.onIntent(BudgetEditIntent.SaveClicked)
            advanceUntilIdle()

            assertFalse(vm.state.value.isVisible)
            assertNull(vm.state.value.formError)
        }

    @Test
    fun `an edit whose category is not in the list shows no total chip`() =
        runTest(mainRule.dispatcher) {
            // The budget row exists but its category row is missing from the list.
            setUp("b1" to "gone")

            val state = open("b1").state.value

            assertEquals("gone", state.categoryId)
            assertTrue(state.choices.isEmpty())
        }

    @Test
    fun `a double tap saves once`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp()
            prompt.asked = true
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetEditIntent.CategoryPicked("food"))
                vm.onIntent(BudgetEditIntent.LimitChanged("1000000"))
                vm.onIntent(BudgetEditIntent.SaveClicked)
                vm.onIntent(BudgetEditIntent.SaveClicked)
                advanceUntilIdle()

                expectNoEvents()
            }
            assertEquals(listOf("food"), budgets.stored.values.map { it.categoryId })
            assertEquals(
                1_000_000L,
                budgets.stored.values
                    .single()
                    .limitAmount,
            )
            assertFalse(vm.state.value.isVisible)
        }

    @Test
    fun `first save asks for the permission and waits for the answer`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp()
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetEditIntent.CategoryPicked("food"))
                vm.onIntent(BudgetEditIntent.LimitChanged("1000000"))
                vm.onIntent(BudgetEditIntent.SaveClicked)
                advanceUntilIdle()

                assertEquals(BudgetEditEffect.AskNotificationPermission, awaitItem())
                // The sheet and its launcher stay until the user answers; a second save cannot run meanwhile.
                assertTrue(vm.state.value.isVisible)
                vm.onIntent(BudgetEditIntent.SaveClicked)
                advanceUntilIdle()
                expectNoEvents()
                assertEquals(1, budgets.stored.size)

                vm.onIntent(BudgetEditIntent.PermissionAnswered)
                assertFalse(vm.state.value.isVisible)
            }

            // Asked once: the next save closes at once.
            val next = open()
            next.onIntent(BudgetEditIntent.CategoryPicked("shopping"))
            next.onIntent(BudgetEditIntent.LimitChanged("500000"))
            next.onIntent(BudgetEditIntent.SaveClicked)
            advanceUntilIdle()
            assertFalse(next.state.value.isVisible)
        }

    @Test
    fun `an edited row that disappears closes the sheet`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp("b1" to "food")
            val vm = open("b1")
            assertTrue(vm.state.value.isVisible)

            // A category delete removes its budgets.
            budgets.delete("b1")
            advanceUntilIdle()

            assertFalse(vm.state.value.isVisible)
        }

    @Test
    fun `delete asks to confirm and closes after`() =
        runTest(mainRule.dispatcher) {
            givenCategories()
            setUp("b1" to "food")
            val vm = open("b1")

            vm.onIntent(BudgetEditIntent.DeleteClicked)
            assertTrue(vm.state.value.isDeleteDialogShown)
            vm.onIntent(BudgetEditIntent.DeleteDismissed)
            assertFalse(vm.state.value.isDeleteDialogShown)
            assertEquals(1, budgets.stored.size)

            vm.onIntent(BudgetEditIntent.DeleteClicked)
            vm.onIntent(BudgetEditIntent.DeleteConfirmed)
            advanceUntilIdle()

            assertTrue(budgets.stored.isEmpty())
            assertFalse(vm.state.value.isDeleteDialogShown)
            assertFalse(vm.state.value.isVisible)
        }

    @Test
    fun `limit keeps digits only and at most 13 of them`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.onIntent(BudgetEditIntent.LimitChanged("1a2.3,4 5"))
            assertEquals("12345", vm.state.value.limitText)

            vm.onIntent(BudgetEditIntent.LimitChanged("12345678901234567"))
            assertEquals("1234567890123", vm.state.value.limitText)
        }
}

private class MutableClock(
    var now: Instant,
) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now
}
