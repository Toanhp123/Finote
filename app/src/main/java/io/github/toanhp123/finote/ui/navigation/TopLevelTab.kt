package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Overview : NavKey

@Serializable
data object Transactions : NavKey

@Serializable
data object Budget : NavKey

@Serializable
data object Settings : NavKey

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
