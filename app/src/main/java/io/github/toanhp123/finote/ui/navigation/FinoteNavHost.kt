package io.github.toanhp123.finote.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.CashEntryRoute
import io.github.toanhp123.finote.ui.cashentry.CashEntryViewModel
import io.github.toanhp123.finote.ui.common.component.layout.FinoteNavBar
import io.github.toanhp123.finote.ui.common.component.layout.FinoteNavItem
import io.github.toanhp123.finote.ui.common.component.layout.FinoteScaffold
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.component.overlay.rememberFinoteSnackbarState
import io.github.toanhp123.finote.ui.common.theme.FinoteMotion
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.SettingsRoute
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsRoute
import io.github.toanhp123.finote.ui.settings.capture.NotificationCaptureSettingsRoute
import io.github.toanhp123.finote.ui.settings.logs.NotificationLogsRoute
import io.github.toanhp123.finote.ui.setup.SetupRoute
import io.github.toanhp123.finote.ui.transactions.TransactionListRoute

@Composable
fun FinoteNavHost(
    shouldStartAtSetup: Boolean,
    isToolsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val start: NavKey = if (shouldStartAtSetup) Setup(isFromOnboarding = true) else Overview
    val backStack = rememberNavBackStack(start)
    val snackbarState = rememberFinoteSnackbarState()
    val motion = FinoteTheme.motion
    FinoteScaffold(
        modifier = modifier,
        snackbarState = snackbarState,
        bottomBar =
            if (isTabBarVisible(backStack)) {
                { TabBar(selected = currentTab(backStack), onSelect = { selectTab(backStack, it) }) }
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
            sceneStrategies = remember { listOf(SheetSceneStrategy()) },
            // A short fade-through replaces the default full-width slide, for tabs and pushed pages alike.
            transitionSpec = { fadeThrough(motion) },
            popTransitionSpec = { fadeThrough(motion) },
            predictivePopTransitionSpec = { fadeThrough(motion) },
            entryProvider =
                entryProvider<NavKey> {
                    entry<Overview> { ComingSoonScreen(R.string.tab_overview) }
                    entry<Transactions> {
                        TransactionListRoute(
                            viewModel = hiltViewModel(),
                            snackbarState = snackbarState,
                            onOpenSetup = { openSetup(backStack) },
                            onOpenEditor = { id -> openEditor(backStack, id) },
                            contentPadding = padding,
                        )
                    }
                    entry<Budget> { ComingSoonScreen(R.string.tab_budget) }
                    settingsEntries(backStack, snackbarState, padding)
                    if (isToolsEnabled) logsEntry(backStack, snackbarState, padding)
                    editorEntry(backStack)
                    entry<Setup> { key ->
                        SetupRoute(
                            viewModel = hiltViewModel(),
                            onFinish = { finishSetup(backStack, key) },
                            modifier = Modifier.padding(padding),
                        )
                    }
                },
        )
    }
}

private fun fadeThrough(motion: FinoteMotion): ContentTransform =
    fadeIn(tween(motion.pageFadeInMillis, delayMillis = motion.pageFadeOutMillis)) togetherWith
        fadeOut(tween(motion.pageFadeOutMillis))

/** The Settings tab and the pages it opens. */
private fun EntryProviderScope<NavKey>.settingsEntries(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
) {
    entry<Settings> {
        SettingsRoute(
            viewModel = hiltViewModel(),
            onOpenCapture = { openNotificationCaptureSettings(backStack) },
            onOpenAccounts = { openAccountsSettings(backStack) },
            onOpenLogs = { openNotificationLogs(backStack) },
            modifier = Modifier.padding(padding),
        )
    }
    entry<AccountsSettings> {
        AccountsSettingsRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onClose = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
        )
    }
    entry<NotificationCaptureSettings> {
        NotificationCaptureSettingsRoute(
            viewModel = hiltViewModel(),
            onClose = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
        )
    }
}

/** The notification log page. Registered only when tools are enabled, so a release build has no way to reach it. */
private fun EntryProviderScope<NavKey>.logsEntry(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
) {
    entry<NotificationLogs> {
        NotificationLogsRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onClose = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
        )
    }
}

/** The edit sheet. It has no inline UI: the sheet opens its own window, so the list below stays visible. */
private fun EntryProviderScope<NavKey>.editorEntry(backStack: MutableList<NavKey>) {
    entry<EditTransaction>(metadata = SheetSceneStrategy.sheet()) { key ->
        CashEntryRoute(
            viewModel =
                hiltViewModel<CashEntryViewModel, CashEntryViewModel.Factory>(
                    creationCallback = { it.create(key.id) },
                ),
            onClose = { closeEditor(backStack, key) },
        )
    }
}

@Composable
private fun TabBar(
    selected: NavKey,
    onSelect: (NavKey) -> Unit,
) {
    val items = TopLevelTabs.map { FinoteNavItem(it.iconRes(), stringResource(it.labelRes())) }
    FinoteNavBar(
        items = items,
        selectedIndex = TopLevelTabs.indexOf(selected),
        onSelect = { index -> onSelect(TopLevelTabs[index]) },
    )
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

/** Opens [AccountsSettings]. A second call while it is on top does nothing, so a double tap cannot stack it. */
private fun openAccountsSettings(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != AccountsSettings) backStack.add(AccountsSettings)
}

/** Opens [NotificationLogs]. A second call while it is on top does nothing, so a double tap cannot stack it. */
private fun openNotificationLogs(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != NotificationLogs) backStack.add(NotificationLogs)
}
