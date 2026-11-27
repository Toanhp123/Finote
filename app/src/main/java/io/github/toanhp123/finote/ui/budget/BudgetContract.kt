package io.github.toanhp123.finote.ui.budget

import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.ui.navigation.EditBudget
import io.github.toanhp123.finote.ui.navigation.ReportList
import java.time.LocalDate
import java.time.YearMonth

/** A category budget with the category it measures. */
data class BudgetRowUi(
    val status: BudgetStatus,
    val category: Category,
) {
    // The status holds money.
    override fun toString() = "BudgetRowUi(budget=${status.budget})"
}

/**
 * [month] is the month shown; [currentMonth] and [today] come from the ViewModel clock, so the screen never reads one.
 * [isAddEnabled] is true in the current month while the total or a shown top-level expense category has no budget.
 */
data class BudgetState(
    val month: YearMonth,
    val currentMonth: YearMonth,
    val today: LocalDate,
    val total: BudgetStatus? = null,
    val rows: List<BudgetRowUi> = emptyList(),
    val isAddEnabled: Boolean = false,
    val isLoading: Boolean = true,
    val hasError: Boolean = false,
) {
    val isCurrentMonth get() = month == currentMonth

    // Rows and the total hold money: counts only.
    override fun toString() =
        "BudgetState(month=$month, hasTotal=${total != null}, rows=${rows.size}, " +
            "isLoading=$isLoading, hasError=$hasError)"
}

sealed interface BudgetIntent {
    /** The screen came to the front; the day may have changed while it was away. */
    data object Resumed : BudgetIntent

    data object PreviousMonth : BudgetIntent

    data object NextMonth : BudgetIntent

    data object TotalClicked : BudgetIntent

    data class RowClicked(
        val categoryId: String,
    ) : BudgetIntent

    data class EditClicked(
        val budgetId: String,
    ) : BudgetIntent

    data object AddClicked : BudgetIntent
}

sealed interface BudgetEffect {
    data class OpenReport(
        val key: ReportList,
    ) : BudgetEffect

    data class OpenEditor(
        val key: EditBudget,
    ) : BudgetEffect
}
