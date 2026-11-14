package io.github.toanhp123.finote.ui.settings.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitchRow
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName

@Composable
internal fun MergeDialog(
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
internal fun MergeBalances(
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

@Composable
internal fun IgnoreDialog(
    dialog: AccountsDialog.Ignore,
    isVisible: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.accounts_ignore_dialog_title),
        text = stringResource(R.string.accounts_ignore_dialog_text),
        onDismiss = { onIntent(AccountsSettingsIntent.DialogDismissed) },
        confirmText = stringResource(R.string.accounts_ignore_confirm),
        onConfirm = { onIntent(AccountsSettingsIntent.IgnoreConfirmed) },
        isDestructive = true,
        content =
            if (dialog.trashableCount > 0) {
                {
                    TrashOption(dialog.trashableCount, dialog.shouldTrash) {
                        onIntent(AccountsSettingsIntent.TrashToggled(it))
                    }
                }
            } else {
                null
            },
    )
}

@Composable
internal fun TrashOption(
    count: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FinoteSwitchRow(
        title = pluralStringResource(R.plurals.accounts_ignore_trash, count, count),
        checked = isChecked,
        onCheckedChange = onCheckedChange,
    )
}
