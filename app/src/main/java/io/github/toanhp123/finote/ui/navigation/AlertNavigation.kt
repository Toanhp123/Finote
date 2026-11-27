package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey

/**
 * Opens the [Budget] tab for a tap on a budget alert. Nothing during onboarding setup or when [Budget] is already on
 * top; otherwise the stack becomes [backStackFor], which also closes a sheet or report list above it.
 * Its own file: NavBackStack.kt is at the detekt function limit and FinoteNavHost.kt at the line limit.
 */
fun openTabFromAlert(backStack: MutableList<NavKey>) {
    if (backStack.firstOrNull() == Setup(isFromOnboarding = true) || backStack.lastOrNull() == Budget) return
    backStack.clear()
    backStack.addAll(backStackFor(Budget))
}
