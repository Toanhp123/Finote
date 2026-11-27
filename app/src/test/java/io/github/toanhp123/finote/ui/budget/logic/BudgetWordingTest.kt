package io.github.toanhp123.finote.ui.budget.logic

import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.model.BudgetState
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.budget.testBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class BudgetWordingTest {
    private val november = YearMonth.of(2026, 11)

    private fun status(
        available: Long,
        spent: Long,
        state: BudgetState,
        month: YearMonth = november,
    ) = BudgetStatus(testBudget("b", month), spent, 0, available, state, percent = null)

    @Test
    fun `over by 1 dong says Vuot with 1 over`() {
        val over = status(available = 1_000, spent = 1_001, state = BudgetState.OVER)

        assertEquals(R.string.budget_word_over, wordOf(over))
        assertEquals(1L, overByOf(over))
        assertNull(leftOf(over))
    }

    @Test
    fun `exactly used up says Het`() {
        val used = status(available = 1_000, spent = 1_000, state = BudgetState.OVER)

        assertEquals(R.string.budget_word_used_up, wordOf(used))
        assertNull(overByOf(used))
        assertNull(leftOf(used))
    }

    @Test
    fun `available below zero says Het even with spending and shows no amount`() {
        val below = status(available = -500, spent = 300, state = BudgetState.OVER)

        assertEquals(R.string.budget_word_used_up, wordOf(below))
        assertNull(overByOf(below))
        assertNull(leftOf(below))
    }

    @Test
    fun `warning says Sap het and still shows what is left`() {
        val warning = status(available = 1_000, spent = 850, state = BudgetState.WARNING)

        assertEquals(R.string.budget_word_near, wordOf(warning))
        assertEquals(150L, leftOf(warning))
    }

    @Test
    fun `normal has no word`() {
        assertNull(wordOf(status(available = 1_000, spent = 100, state = BudgetState.NORMAL)))
    }

    @Test
    fun `per day is left over days left including today and null when nothing is left`() {
        val fresh = status(available = 3_000_000, spent = 0, state = BudgetState.NORMAL)
        // 15 November: 16 days left including today.
        assertEquals(3_000_000L / 16, perDayOf(fresh, LocalDate.of(2026, 11, 15)))
        assertEquals(3_000_000L, perDayOf(fresh, LocalDate.of(2026, 11, 30)))

        val used = status(available = 1_000, spent = 1_000, state = BudgetState.OVER)
        assertNull(perDayOf(used, LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun `per day is hidden for a past month`() {
        val past = status(available = 1_000_000, spent = 0, state = BudgetState.NORMAL, month = YearMonth.of(2026, 10))

        assertNull(perDayOf(past, LocalDate.of(2026, 11, 15)))
    }
}
