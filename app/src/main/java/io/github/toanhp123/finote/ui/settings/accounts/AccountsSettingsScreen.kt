package io.github.toanhp123.finote.ui.settings.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlinx.coroutines.launch

/** Dimmed, not disabled: the text stays readable and the "Ignored" label carries the meaning. */
private const val IGNORED_ALPHA = 0.6f

@Composable
fun AccountsSettingsRoute(
    viewModel: AccountsSettingsViewModel,
    snackbarState: FinoteSnackbarState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val merged = stringResource(R.string.accounts_snack_merged)
    val ignored = stringResource(R.string.accounts_snack_ignored)
    val following = stringResource(R.string.accounts_snack_following)
    val blocked = stringResource(R.string.accounts_snack_transfer)
    val failed = stringResource(R.string.accounts_snack_failed)
    ObserveEffects(viewModel.effects) { effect ->
        val message =
            when (effect) {
                AccountsSettingsEffect.Merged -> merged
                AccountsSettingsEffect.Ignored -> ignored
                AccountsSettingsEffect.FollowingAgain -> following
                AccountsSettingsEffect.MergeBlockedByTransfer -> blocked
                AccountsSettingsEffect.ActionFailed -> failed
            }
        scope.launch { snackbarState.show(message) }
    }
    AccountsSettingsScreen(state, viewModel::onIntent, onClose, modifier)
}

@Composable
fun AccountsSettingsScreen(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(title = stringResource(R.string.settings_accounts_title), onBack = onBack)
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FinoteTheme.spacing.lg),
        ) {
            FinoteCard(modifier = Modifier.fillMaxWidth()) {
                state.accounts.forEach { account -> AccountRow(account, onIntent) }
            }
        }
    }
    AccountSheet(state, onIntent)
    state.pendingMerge?.let { (source, target) -> MergeDialog(source, target, state.mergeCount, onIntent) }
    if (state.isIgnoreDialogOpen) IgnoreDialog(state.trashableCount, onIntent)
}

@Composable
private fun AccountRow(
    account: Account,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    // Only an account with a bank key has actions; cash opens nothing.
    val onClick =
        if (account.bankPackageName == null) null else ({ onIntent(AccountsSettingsIntent.AccountClicked(account.id)) })
    FinoteListRow(
        title = displayName(account.name, account.nameKey),
        modifier = if (account.isIgnored) Modifier.alpha(IGNORED_ALPHA) else Modifier,
        subtitle = if (account.isIgnored) stringResource(R.string.accounts_ignored_label) else null,
        trailing = { MoneyText(amount = account.currentBalance, kind = MoneyKind.Neutral) },
        onClick = onClick,
    )
}

@Composable
private fun AccountSheet(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    FinoteBottomSheet(
        isVisible = state.selected != null,
        onDismiss = { onIntent(AccountsSettingsIntent.SheetDismissed) },
    ) {
        AccountSheetContent(state, onIntent)
    }
}

@Composable
internal fun ColumnScope.AccountSheetContent(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    val account = state.selected ?: return
    Text(
        text = displayName(account.name, account.nameKey),
        modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
        color = FinoteTheme.scheme.onSurface,
        style = FinoteTheme.typography.material.titleLarge,
    )
    MergeTargets(state.mergeTargets, onIntent)
    if (account.isIgnored) {
        FinoteListRow(
            title = stringResource(R.string.accounts_follow_again),
            onClick = { onIntent(AccountsSettingsIntent.FollowAgainClicked) },
        )
    } else {
        FinoteListRow(
            title = stringResource(R.string.accounts_ignore_action),
            onClick = { onIntent(AccountsSettingsIntent.IgnoreClicked) },
        )
    }
}

@Composable
private fun MergeTargets(
    targets: List<Account>,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    Column {
        Text(
            text = stringResource(R.string.accounts_merge_into),
            modifier = Modifier.padding(vertical = FinoteTheme.spacing.sm).semantics { heading() },
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        if (targets.isEmpty()) {
            Text(
                text = stringResource(R.string.accounts_merge_none),
                modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodyMedium,
            )
        }
        targets.forEach { target ->
            FinoteListRow(
                title = displayName(target.name, target.nameKey),
                trailing = { MoneyText(amount = target.currentBalance, kind = MoneyKind.Neutral) },
                onClick = { onIntent(AccountsSettingsIntent.MergeTargetPicked(target.id)) },
            )
        }
    }
}
