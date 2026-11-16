package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsState

@Composable
fun AccountSheet(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    // The sheet keeps its content while it slides out.
    val shownAccount = rememberLastNonNull(state.selected)
    FinoteBottomSheet(
        isVisible = state.selected != null,
        onDismiss = { onIntent(AccountsSettingsIntent.SheetDismissed) },
    ) {
        shownAccount?.let { AccountSheetContent(it, state.mergeTargets, onIntent) }
    }
}

@Composable
private fun ColumnScope.AccountSheetContent(
    account: Account,
    mergeTargets: List<Account>,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    Text(
        text = displayName(account.name, account.nameKey),
        modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
        color = FinoteTheme.scheme.onSurface,
        style = FinoteTheme.typography.material.titleLarge,
    )
    MergeTargets(mergeTargets, onIntent)
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
