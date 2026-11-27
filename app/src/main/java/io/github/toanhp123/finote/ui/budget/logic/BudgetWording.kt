package io.github.toanhp123.finote.ui.budget.logic

import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.model.BudgetState
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import java.time.LocalDate
import java.time.YearMonth

/** The word a state always carries next to its color, so color is never the only signal. */
@StringRes
fun wordOf(status: BudgetStatus): Int? =
    when {
        status.available <= 0 || status.spent == status.available -> R.string.budget_word_used_up
        status.spent > status.available -> R.string.budget_word_over
        status.state == BudgetState.WARNING -> R.string.budget_word_near
        else -> null
    }

/** How far past the available amount; null when nothing is available, because then there is no figure to be over. */
fun overByOf(status: BudgetStatus): Long? =
    if (status.available > 0 && status.spent > status.available) {
        Math.subtractExact(status.spent, status.available)
    } else {
        null
    }

/** What is left; null when nothing is available or nothing is left. */
fun leftOf(status: BudgetStatus): Long? = status.remaining.takeIf { status.available > 0 && it > 0 }

/** [leftOf] over the days left in the month including [today]; the current month only. */
fun perDayOf(
    status: BudgetStatus,
    today: LocalDate,
): Long? {
    val month = status.budget.month
    if (month != YearMonth.from(today)) return null
    return leftOf(status)?.div(month.lengthOfMonth() - today.dayOfMonth + 1)
}
