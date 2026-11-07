package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey

/** The tabs of the bottom bar, in display order. */
val TopLevelTabs: List<NavKey> = listOf(Overview, Transactions, Budget, Settings)

/** Back stack after picking [tab]: [Overview] alone, or [Overview, tab]. */
fun backStackFor(tab: NavKey): List<NavKey> = if (tab == Overview) listOf(Overview) else listOf(Overview, tab)

/** The tab to highlight: the last top-level key on [backStack], or Overview. */
fun currentTab(backStack: List<NavKey>): NavKey = backStack.lastOrNull { it in TopLevelTabs } ?: Overview

/** Picks [tab]: nothing when it is already the current tab, else the stack becomes [backStackFor]. */
fun selectTab(
    backStack: MutableList<NavKey>,
    tab: NavKey,
) {
    if (currentTab(backStack) == tab) return
    backStack.clear()
    backStack.addAll(backStackFor(tab))
}

/** Opens the banner flow of [Setup]. A second call while it is on top does nothing, so a double tap cannot stack it. */
fun openSetup(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() !is Setup) backStack.add(Setup(isFromOnboarding = false))
}

/** Leaves [key]: onboarding replaces the stack with [Overview], the banner flow goes back to the screen below. */
fun finishSetup(
    backStack: MutableList<NavKey>,
    key: Setup,
) {
    if (key.isFromOnboarding) {
        backStack.clear()
        backStack.add(Overview)
    } else {
        backStack.removeLastOrNull()
    }
}
