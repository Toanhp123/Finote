package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey

/** Opens [EditBudget] ([id] null creates). While a budget editor is on top this does nothing: a double tap is safe. */
fun openBudgetEditor(
    backStack: MutableList<NavKey>,
    id: String? = null,
) {
    if (backStack.lastOrNull() !is EditBudget) backStack.add(EditBudget(id))
}

/** Closes the budget editor [key]. A second call does nothing. */
fun closeBudgetEditor(
    backStack: MutableList<NavKey>,
    key: EditBudget,
) {
    backStack.remove(key)
}
