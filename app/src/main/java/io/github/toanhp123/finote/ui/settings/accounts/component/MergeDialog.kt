package io.github.toanhp123.finote.ui.settings.accounts.component

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.settings.accounts.AccountsDialog
import io.github.toanhp123.finote.ui.settings.accounts.AccountsSettingsIntent

@Composable
fun MergeDialog(
    dialog: AccountsDialog.Merge,
    isVisible: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    val (source, target, count) = dialog
    FinoteDialog(
        isVisible = isVisible,
        title =
            stringResource(
                R.string.accounts_merge_dialog_title,
                displayName(source.name, source.nameKey),
                displayName(target.name, target.nameKey),
            ),
        text = pluralStringResource(R.plurals.accounts_merge_dialog_text, count, count),
        onDismiss = { onIntent(AccountsSettingsIntent.DialogDismissed) },
        confirmText = stringResource(R.string.accounts_merge_confirm),
        onConfirm = { onIntent(AccountsSettingsIntent.MergeConfirmed) },
        isDestructive = true,
        content = { MergeBalances(source, target) },
    )
}

/** Both balances, so the user sees what each side holds before the merge. */
@Composable
private fun MergeBalances(
    source: Account,
    target: Account,
) {
    Column {
        listOf(source, target).forEach { account ->
            FinoteListRow(
                title = displayName(account.name, account.nameKey),
                trailing = { MoneyText(amount = account.currentBalance, kind = MoneyKind.Neutral) },
            )
        }
    }
}
