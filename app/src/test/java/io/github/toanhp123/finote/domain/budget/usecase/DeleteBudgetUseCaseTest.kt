package io.github.toanhp123.finote.domain.budget.usecase

import io.github.toanhp123.finote.domain.budget.FakeBudgetRepository
import io.github.toanhp123.finote.domain.budget.error.BudgetError
import io.github.toanhp123.finote.domain.budget.testBudget
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class DeleteBudgetUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-11-15T05:00:00Z"), ZoneOffset.UTC)
    private val budgets = FakeBudgetRepository()
    private val delete = DeleteBudgetUseCase(budgets, FakeTransactionRunner(), clock)

    @Test
    fun `deletes a current month row for good`() =
        runTest {
            budgets.stored["b"] = testBudget("b", YearMonth.of(2026, 11), "food")

            assertEquals(Outcome.Ok(Unit), delete("b"))
            assertNull(budgets.get("b"))
        }

    @Test
    fun `a past month row is NotCurrentMonth and stays`() =
        runTest {
            budgets.stored["b"] = testBudget("b", YearMonth.of(2026, 10), "food")

            assertEquals(Outcome.Err(BudgetError.NotCurrentMonth), delete("b"))
            assertEquals("b", budgets.get("b")?.id)
        }

    @Test
    fun `an unknown id is NotFound`() =
        runTest {
            assertEquals(Outcome.Err(BudgetError.NotFound), delete("missing"))
        }
}
