package io.github.toanhp123.finote.ui.transactions

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.display.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.finance.TransactionRow
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.format.iconRes
import io.github.toanhp123.finote.ui.common.rememberNotificationListenerEnabled
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.transactions.component.BalanceDriftBanner
import io.github.toanhp123.finote.ui.transactions.component.DeleteAdjustmentDialog
import io.github.toanhp123.finote.ui.transactions.component.FailedInboxBanner
import io.github.toanhp123.finote.ui.transactions.component.ListenerOffBanner
import io.github.toanhp123.finote.ui.transactions.component.ReconcileSheet
import io.github.toanhp123.finote.ui.transactions.component.TransactionFilterChips
import io.github.toanhp123.finote.ui.transactions.logic.DayLabel
import io.github.toanhp123.finote.ui.transactions.logic.dayLabel
import io.github.toanhp123.finote.ui.transactions.logic.fallbackIcon
import io.github.toanhp123.finote.ui.transactions.logic.fallbackTitle
import io.github.toanhp123.finote.ui.transactions.logic.isOpenable
import io.github.toanhp123.finote.ui.transactions.logic.moneyKindOf
import io.github.toanhp123.finote.ui.transactions.logic.rowSubtitle
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val SKELETON_ROWS = 6

@Composable
fun TransactionListRoute(
    viewModel: TransactionListViewModel,
    snackbarState: FinoteSnackbarState,
    onOpenSetup: () -> Unit,
    onOpenEditor: (String) -> Unit,
    onOpenInbox: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val deleteError = stringResource(R.string.transactions_delete_error)
    val reconcileError = stringResource(R.string.reconcile_error)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is TransactionListEffect.OpenEditor -> {
                onOpenEditor(effect.id)
            }

            TransactionListEffect.OpenInbox -> {
                onOpenInbox()
            }

            TransactionListEffect.DeleteFailed -> {
                scope.launch { snackbarState.show(deleteError) }
            }

            TransactionListEffect.ReconcileFailed -> {
                scope.launch { snackbarState.show(reconcileError) }
            }
        }
    }
    TransactionListScreen(
        state = state,
        onIntent = viewModel::onIntent,
        modifier = modifier,
        contentPadding = contentPadding,
        isListenerOff = !rememberNotificationListenerEnabled(),
        onOpenSetup = onOpenSetup,
    )
}

/** [contentPadding] keeps the last row clear of the floating nav bar. [isListenerOff] shows the banner. */
@Composable
fun TransactionListScreen(
    state: TransactionListState,
    onIntent: (TransactionListIntent) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    isListenerOff: Boolean = false,
    onOpenSetup: () -> Unit = {},
) {
    // The dialog is drawn after the body in the same Box, so an inline overlay (preview mode) covers the top bar.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            FinoteTopBar(title = stringResource(R.string.tab_transactions))
            if (isListenerOff) {
                ListenerOffBanner(
                    onClick = onOpenSetup,
                    modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
                )
            }
            if (state.inboxBannerCount > 0) {
                FailedInboxBanner(
                    count = state.inboxBannerCount,
                    onReview = { onIntent(TransactionListIntent.InboxBannerClicked) },
                    onClose = { onIntent(TransactionListIntent.InboxBannerClosed) },
                    modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
                )
            }
            DriftBanners(state, onIntent)
            if (state.isLoading) {
                LoadingRows()
            } else {
                // The chips stay on an error: picking another account retries.
                TransactionFilterChips(state, onIntent)
                when {
                    state.hasError -> {
                        FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(R.string.transactions_error))
                    }

                    state.days.isEmpty() -> {
                        FinoteEmptyState(R.drawable.ic_receipt_long, stringResource(state.emptyTitle()))
                    }

                    else -> {
                        DayList(state, onIntent, contentPadding)
                    }
                }
            }
        }
        DeleteAdjustmentDialog(isVisible = state.deletingId != null, onIntent = onIntent)
        ReconcileSheet(
            account = state.reconcilingAccountId?.let(state.accountsById::get),
            onSubmit = { onIntent(TransactionListIntent.ReconcileSubmitted(it)) },
            onDismiss = { onIntent(TransactionListIntent.ReconcileDismissed) },
        )
    }
}

@Composable
private fun DriftBanners(
    state: TransactionListState,
    onIntent: (TransactionListIntent) -> Unit,
) {
    val resources = LocalResources.current
    // One banner for the first drift; the rest only count, so many drifting accounts do not push the list away.
    val shown = state.drifts.filter { it.accountId in state.accountsById }
    val drift = shown.firstOrNull() ?: return
    val account = state.accountsById.getValue(drift.accountId)
    BalanceDriftBanner(
        accountName = resources.displayName(account.name, account.nameKey),
        onReconcile = { onIntent(TransactionListIntent.ReconcileClicked(drift.accountId)) },
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        otherCount = shown.size - 1,
    )
}

@StringRes
private fun TransactionListState.emptyTitle(): Int =
    when {
        isPendingOnly -> R.string.transactions_empty_pending
        selectedAccountId == null -> R.string.transactions_empty
        else -> R.string.transactions_empty_account
    }

@Composable
private fun LoadingRows() {
    Column(
        modifier = Modifier.padding(horizontal = FinoteTheme.spacing.lg),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        repeat(SKELETON_ROWS) { FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget)) }
    }
}

@Composable
private fun DayList(
    state: TransactionListState,
    onIntent: (TransactionListIntent) -> Unit,
    contentPadding: PaddingValues,
) {
    val resources = LocalResources.current
    val accountNames =
        remember(state.accountsById, resources) {
            state.accountsById.mapValues { resources.displayName(it.value.name, it.value.nameKey) }
        }
    // A row on an archived or ignored account stays read-only.
    val openableAccountIds = remember(state.accounts) { state.accounts.mapTo(mutableSetOf()) { it.id } }
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        state.days.forEach { day ->
            item(key = "day-${day.date}") { DayHeader(day, state.today, showNet = !state.isPendingOnly) }
            items(day.transactions, key = { it.id }) { transaction ->
                TransactionItem(
                    transaction = transaction,
                    isAutoPaired = transaction.id in state.autoPairedIds,
                    selectedAccountId = state.selectedAccountId,
                    modifier = Modifier.animateItem(),
                    category = transaction.categoryId?.let(state.categoriesById::get),
                    accountNames = accountNames,
                    onClick =
                        if (transaction.isOpenable(openableAccountIds)) {
                            { onIntent(TransactionListIntent.RowClicked(transaction.id)) }
                        } else {
                            null
                        },
                )
            }
        }
    }
}

@Composable
private fun DayHeader(
    day: TransactionDay,
    today: LocalDate,
    showNet: Boolean,
) {
    val label =
        when (val label = dayLabel(day.date, today, LocalConfiguration.current.locales[0])) {
            DayLabel.Today -> stringResource(R.string.transactions_today)
            DayLabel.Yesterday -> stringResource(R.string.transactions_yesterday)
            is DayLabel.Date -> label.text
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).semantics { heading() },
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.titleSmall,
        )
        if (showNet) DayNet(day.net)
    }
}

// MoneyText writes the sign itself, so a negative net goes in as its absolute value with the Expense kind.
@Composable
private fun DayNet(net: Long) {
    when {
        net > 0 -> MoneyText(net, MoneyKind.Income)
        net < 0 -> MoneyText(Math.negateExact(net), MoneyKind.Expense)
        else -> MoneyText(0, MoneyKind.Neutral)
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    category: Category?,
    accountNames: Map<String, String>,
    isAutoPaired: Boolean,
    selectedAccountId: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val deletedLabel = stringResource(R.string.transactions_deleted_account)
    val title = category?.let { displayName(it.name, it.nameKey) } ?: stringResource(transaction.type.fallbackTitle())
    TransactionRow(
        title = title,
        subtitle =
            rowSubtitle(
                transaction,
                accountNames::get,
                deletedLabel,
                pendingLabel = stringResource(R.string.reimbursement_pending),
                settledLabel = stringResource(R.string.reimbursement_settled),
                autoPairedLabel = stringResource(R.string.transactions_auto_paired).takeIf { isAutoPaired },
                excludedLabel = stringResource(R.string.transactions_not_in_reports),
            ),
        amount = transaction.amount,
        kind = moneyKindOf(transaction),
        iconRes = category?.let { iconRes(it.icon) } ?: transaction.type.fallbackIcon(),
        colorArgb = category?.color ?: FinoteTheme.scheme.onSurfaceVariant.toArgb(),
        modifier = modifier.padding(horizontal = FinoteTheme.spacing.lg),
        // Like the day net: a transfer seen from its destination account carries no fee.
        fee = if (selectedAccountId == null || selectedAccountId == transaction.accountId) transaction.feeAmount else 0,
        isMuted = transaction.isExcludedFromAnalytics,
        onClick = onClick,
    )
}
