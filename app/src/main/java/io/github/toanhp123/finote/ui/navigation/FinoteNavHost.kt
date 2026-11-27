package io.github.toanhp123.finote.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditRoute
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditViewModel
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsRoute
import io.github.toanhp123.finote.ui.settings.capture.NotificationCaptureSettingsRoute
import io.github.toanhp123.finote.ui.settings.categories.CategoriesSettingsRoute
import io.github.toanhp123.finote.ui.settings.categoryedit.CategoryEditRoute
import io.github.toanhp123.finote.ui.settings.categoryedit.CategoryEditViewModel
import io.github.toanhp123.finote.ui.settings.inbox.FailedInboxRoute
import io.github.toanhp123.finote.ui.settings.keywordruleedit.KeywordRuleEditRoute
import io.github.toanhp123.finote.ui.settings.keywordruleedit.KeywordRuleEditViewModel
import io.github.toanhp123.finote.ui.settings.keywordrules.KeywordRulesRoute
import io.github.toanhp123.finote.ui.settings.logs.NotificationLogsRoute
import io.github.toanhp123.finote.ui.setup.SetupRoute
import io.github.toanhp123.finote.ui.transactions.TransactionListRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun FinoteNavHost(
    shouldStartAtSetup: Boolean,
    isToolsEnabled: Boolean,
    openTabRequest: Int,
    modifier: Modifier = Modifier,
) {
    val start: NavKey = if (shouldStartAtSetup) Setup(isFromOnboarding = true) else Overview
    val backStack = rememberNavBackStack(start)
    // The activity bumps the counter on a tap on a budget alert; 0 means no request.
    LaunchedEffect(openTabRequest) { if (openTabRequest > 0) openTabFromAlert(backStack) }
    val snackbarState = rememberFinoteSnackbarState()
    val motion = FinoteTheme.motion
    val navBar by hiltViewModel<NavBarViewModel>().state.collectAsStateWithLifecycle()
    // The booking sheet leaves before its message ends, so the message belongs to this scope, not to the entry.
    val scope = rememberCoroutineScope()
    val handled = stringResource(R.string.inbox_snack_handled)
    FinoteScaffold(
        modifier = modifier,
        snackbarState = snackbarState,
        bottomBar =
            if (isTabBarVisible(backStack)) {
                {
                    TabBar(
                        selected = currentTab(backStack),
                        hasSettingsBadge = navBar.hasSettingsBadge,
                        onSelect = { selectTab(backStack, it) },
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
            sceneStrategies = remember { listOf(SheetSceneStrategy()) },
            // A short fade-through replaces the default full-width slide, for tabs and pushed pages alike.
            transitionSpec = { fadeThrough(motion) },
            popTransitionSpec = { fadeThrough(motion) },
            predictivePopTransitionSpec = { fadeThrough(motion) },
            entryProvider =
                entryProvider<NavKey> {
                    overviewEntry(backStack, padding)
                    reportListEntry(backStack, padding)
                    entry<Transactions> {
                        TransactionListRoute(
                            viewModel = hiltViewModel(),
                            snackbarState = snackbarState,
                            onOpenSetup = { openSetup(backStack) },
                            onOpenEditor = { id -> openEditor(backStack, id) },
                            onOpenInbox = { openFailedInbox(backStack) },
                            contentPadding = padding,
                        )
                    }
                    budgetEntry(backStack, padding)
                    settingsEntries(backStack, snackbarState, padding)
                    accountEditEntry(backStack, snackbarState, padding, scope)
                    categoryEditEntry(backStack, snackbarState, padding)
                    keywordRuleEntries(backStack, snackbarState, padding)
                    inboxEntry(backStack, snackbarState, padding)
                    if (isToolsEnabled) logsEntry(backStack, snackbarState, padding)
                    editorEntry(backStack)
                    bookingEntry(backStack) { scope.launch { snackbarState.show(handled) } }
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
            onOpenCategories = { openCategoriesSettings(backStack) },
            onOpenKeywordRules = { openPage(backStack, KeywordRulesSettings) },
            onOpenInbox = { openFailedInbox(backStack) },
            onOpenLogs = { openNotificationLogs(backStack) },
            modifier = Modifier.padding(padding),
        )
    }
    entry<AccountsSettings> {
        AccountsSettingsRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onOpenAccount = { key -> openPage(backStack, key) },
            onClose = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
        )
    }
    entry<CategoriesSettings> {
        CategoriesSettingsRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onOpenCategory = { key -> openPage(backStack, key) },
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

/**
 * The account create and edit screen, full screen. The message of a close that the account caused is shown from
 * [scope], the host's, because the entry is gone before the message ends.
 */
private fun EntryProviderScope<NavKey>.accountEditEntry(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
    scope: CoroutineScope,
) {
    entry<EditAccount> { key ->
        AccountEditRoute(
            viewModel =
                hiltViewModel<AccountEditViewModel, AccountEditViewModel.Factory>(
                    creationCallback = { it.create(key.id, key.mergeTargetId) },
                ),
            snackbarState = snackbarState,
            onClose = { message ->
                backStack.remove(key)
                if (message != null) scope.launch { snackbarState.show(message) }
            },
            // The edit screen applies the keyboard inset itself, so the padding must say it already took the bar inset.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** The category create and edit screen, full screen. A new subcategory opens another one on top of it. */
private fun EntryProviderScope<NavKey>.categoryEditEntry(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
) {
    entry<EditCategory> { key ->
        CategoryEditRoute(
            viewModel =
                hiltViewModel<CategoryEditViewModel, CategoryEditViewModel.Factory>(
                    creationCallback = { it.create(key) },
                ),
            snackbarState = snackbarState,
            onClose = { backStack.remove(key) },
            onOpenCategory = { openPage(backStack, it) },
            // The edit screen applies the keyboard inset itself, so the padding must say it already took the bar inset.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** The keyword rules page and its create and edit screen, full screen. */
private fun EntryProviderScope<NavKey>.keywordRuleEntries(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
) {
    entry<KeywordRulesSettings> {
        KeywordRulesRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onOpenRule = { key -> openPage(backStack, key) },
            onClose = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
        )
    }
    entry<EditKeywordRule> { key ->
        KeywordRuleEditRoute(
            viewModel =
                hiltViewModel<KeywordRuleEditViewModel, KeywordRuleEditViewModel.Factory>(
                    creationCallback = { it.create(key) },
                ),
            snackbarState = snackbarState,
            onClose = { backStack.remove(key) },
            // The edit screen applies the keyboard inset itself, so the padding must say it already took the bar inset.
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        )
    }
}

/** The failed notifications inbox. Every build has it. */
private fun EntryProviderScope<NavKey>.inboxEntry(
    backStack: MutableList<NavKey>,
    snackbarState: FinoteSnackbarState,
    padding: PaddingValues,
) {
    entry<FailedInbox> {
        FailedInboxRoute(
            viewModel = hiltViewModel(),
            snackbarState = snackbarState,
            onOpenBooking = { itemId -> openBooking(backStack, itemId) },
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

/** The booking sheet of a failed inbox item: the cash entry sheet in from-log mode, drawn over the inbox. */
private fun EntryProviderScope<NavKey>.bookingEntry(
    backStack: MutableList<NavKey>,
    onShowStaleNotice: () -> Unit,
) {
    entry<BookInboxItem>(metadata = SheetSceneStrategy.sheet()) { key ->
        CashEntryRoute(
            viewModel =
                hiltViewModel<CashEntryViewModel, CashEntryViewModel.Factory>(
                    creationCallback = { it.create(null, key.itemId) },
                ),
            onClose = { backStack.remove(key) },
            onShowStaleNotice = onShowStaleNotice,
        )
    }
}

@Composable
private fun TabBar(
    selected: NavKey,
    hasSettingsBadge: Boolean,
    onSelect: (NavKey) -> Unit,
) {
    val items =
        TopLevelTabs.map {
            FinoteNavItem(
                it.iconRes(),
                stringResource(it.labelRes()),
                hasBadge =
                    it == Settings && hasSettingsBadge,
            )
        }
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

/** Opens [CategoriesSettings]. A second call while it is on top does nothing, so a double tap cannot stack it. */
private fun openCategoriesSettings(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != CategoriesSettings) backStack.add(CategoriesSettings)
}

/** Opens [NotificationLogs]. A second call while it is on top does nothing, so a double tap cannot stack it. */
private fun openNotificationLogs(backStack: MutableList<NavKey>) {
    if (backStack.lastOrNull() != NotificationLogs) backStack.add(NotificationLogs)
}

/** Opens [key]. A second call while it is on top does nothing, so a double tap cannot stack it. */
private fun openPage(
    backStack: MutableList<NavKey>,
    key: NavKey,
) {
    if (backStack.lastOrNull() != key) backStack.add(key)
}
