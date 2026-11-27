package io.github.toanhp123.finote.ui.budget

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.usecase.FakeAccountRepository
import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.budget.usecase.EnsureBudgetMonthUseCase
import io.github.toanhp123.finote.domain.budget.usecase.ObserveBudgetMonthUseCase
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportInputUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.FakeTransactionRepository
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.navigation.EditBudget
import io.github.toanhp123.finote.ui.navigation.ReportList
import io.github.toanhp123.finote.ui.navigation.ReportListKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val october = YearMonth.of(2026, 10)
    private val november = YearMonth.of(2026, 11)
    private val clock = MutableClock(Instant.parse("2026-11-15T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"))
    private val budgets = FakeBudgetRepository()
    private val categories = FakeCategoryRepository()

    private fun TestScope.open(handle: SavedStateHandle = SavedStateHandle()): BudgetViewModel {
        val reportInput =
            ObserveReportInputUseCase(FakeTransactionRepository(), FakeAccountRepository(), categories, clock)
        return BudgetViewModel(
            savedStateHandle = handle,
            observeBudgetMonth =
                ObserveBudgetMonthUseCase(
                    budgets,
                    EnsureBudgetMonthUseCase(budgets, FakeTransactionRunner(), clock),
                    reportInput,
                    clock,
                ),
            observeCategories = ObserveCategoriesUseCase(categories),
            clock = clock,
            isDebuggable = true,
        ).also { advanceUntilIdle() }
    }

    private fun setUp(
        month: YearMonth,
        vararg rows: Pair<String, String?>,
    ) {
        budgets.setUp += month
        rows.forEach { (id, category) -> budgets.stored[id] = testBudget(id, month, category) }
    }

    @Test
    fun `rows open the report of their bucket`() =
        runTest(mainRule.dispatcher) {
            categories.stored["food"] = testCategory("food")
            setUp(november, "b1" to "food")
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetIntent.RowClicked("food"))
                assertEquals(
                    BudgetEffect.OpenReport(ReportList("2026-11", ReportListKind.CATEGORY, "food")),
                    awaitItem(),
                )
            }
            assertEquals(
                listOf("food"),
                vm.state.value.rows
                    .map { it.category.id },
            )
        }

    @Test
    fun `total opens all spending`() =
        runTest(mainRule.dispatcher) {
            setUp(november, "t" to null)
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetIntent.TotalClicked)
                assertEquals(
                    BudgetEffect.OpenReport(ReportList("2026-11", ReportListKind.ALL_SPENDING)),
                    awaitItem(),
                )
            }
        }

    @Test
    fun `edit and add open the editor`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.effects.test {
                vm.onIntent(BudgetIntent.EditClicked("b1"))
                assertEquals(BudgetEffect.OpenEditor(EditBudget("b1")), awaitItem())
                vm.onIntent(BudgetIntent.AddClicked)
                assertEquals(BudgetEffect.OpenEditor(EditBudget()), awaitItem())
            }
        }

    @Test
    fun `an empty current month can add a budget`() =
        runTest(mainRule.dispatcher) {
            val state = open().state.value

            assertFalse(state.isLoading)
            assertTrue(state.isAddEnabled)
        }

    @Test
    fun `add stays on while a shown expense category has no budget and goes off when all have one`() =
        runTest(mainRule.dispatcher) {
            categories.stored["food"] = testCategory("food")
            categories.stored["pay"] = testCategory("pay", type = CategoryType.INCOME)
            categories.stored["hidden"] = testCategory("hidden").copy(isHidden = true)
            categories.stored["sub"] = testCategory("sub", parentId = "food")
            setUp(november, "t" to null)
            assertTrue(open().state.value.isAddEnabled)

            budgets.stored["b1"] = testBudget("b1", november, "food")
            assertFalse(open().state.value.isAddEnabled)
        }

    @Test
    fun `past month hides add and edit`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            assertTrue(vm.state.value.isAddEnabled)

            vm.onIntent(BudgetIntent.PreviousMonth)
            advanceUntilIdle()

            assertEquals(october, vm.state.value.month)
            assertFalse(vm.state.value.isAddEnabled)
            assertFalse(vm.state.value.isCurrentMonth)
        }

    @Test
    fun `next month is refused at the current month`() =
        runTest(mainRule.dispatcher) {
            val vm = open()

            vm.onIntent(BudgetIntent.NextMonth)
            advanceUntilIdle()
            assertEquals(november, vm.state.value.month)

            vm.onIntent(BudgetIntent.PreviousMonth)
            vm.onIntent(BudgetIntent.NextMonth)
            advanceUntilIdle()
            assertEquals(november, vm.state.value.month)
        }

    @Test
    fun `month survives process death`() =
        runTest(mainRule.dispatcher) {
            val handle = SavedStateHandle()
            open(handle).onIntent(BudgetIntent.PreviousMonth)
            advanceUntilIdle()

            assertEquals(october, open(handle).state.value.month)
        }

    @Test
    fun `a new day on resume moves the current month`() =
        runTest(mainRule.dispatcher) {
            val vm = open()
            assertEquals(LocalDate.of(2026, 11, 15), vm.state.value.today)

            clock.now = Instant.parse("2026-12-01T05:00:00Z")
            vm.onIntent(BudgetIntent.Resumed)
            advanceUntilIdle()

            assertEquals(YearMonth.of(2026, 12), vm.state.value.currentMonth)
            assertEquals(LocalDate.of(2026, 12, 1), vm.state.value.today)
        }
}

private class MutableClock(
    var now: Instant,
    private val zone: ZoneId,
) : Clock() {
    override fun getZone(): ZoneId = zone

    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

    override fun instant(): Instant = now
}
