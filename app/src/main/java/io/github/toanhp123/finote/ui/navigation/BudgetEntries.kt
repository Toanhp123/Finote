package io.github.toanhp123.finote.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import io.github.toanhp123.finote.ui.budget.BudgetRoute
import io.github.toanhp123.finote.ui.budgetedit.BudgetEditRoute
import io.github.toanhp123.finote.ui.budgetedit.BudgetEditViewModel

/** The Budget tab. A row opens the spending behind it; an edit button or + opens the edit sheet over it. */
internal fun EntryProviderScope<NavKey>.budgetEntry(
    backStack: MutableList<NavKey>,
    padding: PaddingValues,
) {
    entry<Budget> {
        BudgetRoute(
            viewModel = hiltViewModel(),
            // A second tap while a report list is on top does nothing, so a double tap cannot stack it.
            onOpenReport = { key -> if (backStack.lastOrNull() !is ReportList) backStack.add(key) },
            onOpenEditor = { key -> openBudgetEditor(backStack, key.id) },
            contentPadding = padding,
        )
    }
    // The sheet opens its own window, so the Budget tab stays visible under it.
    entry<EditBudget>(metadata = SheetSceneStrategy.sheet()) { key ->
        BudgetEditRoute(
            viewModel =
                hiltViewModel<BudgetEditViewModel, BudgetEditViewModel.Factory>(
                    creationCallback = { it.create(key.id) },
                ),
            onClose = { closeBudgetEditor(backStack, key) },
        )
    }
}
