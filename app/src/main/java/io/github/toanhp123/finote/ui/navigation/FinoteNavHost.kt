package io.github.toanhp123.finote.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.layout.FinoteNavBar
import io.github.toanhp123.finote.ui.common.component.layout.FinoteNavItem
import io.github.toanhp123.finote.ui.common.component.layout.FinoteScaffold
import io.github.toanhp123.finote.ui.transactions.TransactionListRoute

@Composable
fun FinoteNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(Overview)
    FinoteScaffold(
        modifier = modifier,
        bottomBar =
            if (backStack.lastOrNull() in TopLevelTabs) {
                {
                    val items = TopLevelTabs.map { FinoteNavItem(it.iconRes(), stringResource(it.labelRes())) }
                    val selected = currentTab(backStack)
                    FinoteNavBar(
                        items = items,
                        selectedIndex = TopLevelTabs.indexOf(selected),
                        onSelect = { index -> selectTab(backStack, TopLevelTabs[index]) },
                    )
                }
            } else {
                null
            },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider =
                entryProvider<NavKey> {
                    entry<Overview> { ComingSoonScreen(R.string.tab_overview) }
                    entry<Transactions> { TransactionListRoute(hiltViewModel(), contentPadding = padding) }
                    entry<Budget> { ComingSoonScreen(R.string.tab_budget) }
                    entry<Settings> { ComingSoonScreen(R.string.tab_settings) }
                },
        )
    }
}

@DrawableRes
private fun NavKey.iconRes(): Int =
    when (this) {
        Overview -> R.drawable.ic_home
        Transactions -> R.drawable.ic_receipt_long
        Budget -> R.drawable.ic_pie_chart
        Settings -> R.drawable.ic_settings
        else -> error("not a top-level tab: $this")
    }

@StringRes
private fun NavKey.labelRes(): Int =
    when (this) {
        Overview -> R.string.tab_overview
        Transactions -> R.string.tab_transactions
        Budget -> R.string.tab_budget
        Settings -> R.string.tab_settings
        else -> error("not a top-level tab: $this")
    }
