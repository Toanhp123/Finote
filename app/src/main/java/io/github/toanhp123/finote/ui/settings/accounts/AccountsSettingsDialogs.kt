package io.github.toanhp123.finote.ui.settings.accounts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitch
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName

@Composable
internal fun MergeDialog(
    source: Account,
    target: Account,
    count: Int,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = true,
        title =
            stringResource(
                R.string.accounts_merge_dialog_title,
                displayName(source.name, source.nameKey),
                displayName(target.name, target.nameKey),
            ),
        text = pluralStringResource(R.plurals.accounts_merge_dialog_text, count, count),
        onDismiss = { onIntent(AccountsSettingsIntent.DialogDismissed) },
        dismiss = { CancelButton { onIntent(AccountsSettingsIntent.DialogDismissed) } },
        confirm = {
            FinoteButton(
                text = stringResource(R.string.accounts_merge_confirm),
                onClick = { onIntent(AccountsSettingsIntent.MergeConfirmed) },
                style = FinoteButtonStyle.Danger,
            )
        },
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
    trashableCount: Int,
    onIntent: (AccountsSettingsIntent) -> Unit,
) {
    // Local to the dialog: it is composed only while open, so every opening starts unticked.
    var shouldTrash by remember { mutableStateOf(false) }
    FinoteDialog(
        isVisible = true,
        title = stringResource(R.string.accounts_ignore_dialog_title),
        text = stringResource(R.string.accounts_ignore_dialog_text),
        onDismiss = { onIntent(AccountsSettingsIntent.DialogDismissed) },
        dismiss = { CancelButton { onIntent(AccountsSettingsIntent.DialogDismissed) } },
        confirm = {
            FinoteButton(
                text = stringResource(R.string.accounts_ignore_confirm),
                onClick = { onIntent(AccountsSettingsIntent.IgnoreConfirmed(shouldTrash && trashableCount > 0)) },
                style = FinoteButtonStyle.Danger,
            )
        },
        content =
            if (trashableCount > 0) {
                { TrashOption(trashableCount, shouldTrash) { shouldTrash = it } }
            } else {
                null
            },
    )
}

/** The row owns the tap and announces its label with the state; the switch is only the picture. */
@Composable
internal fun TrashOption(
    count: Int,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    FinoteListRow(
        title = pluralStringResource(R.plurals.accounts_ignore_trash, count, count),
        modifier = Modifier.toggleable(value = isChecked, role = Role.Switch, onValueChange = onCheckedChange),
        trailing = { FinoteSwitch(checked = isChecked, onCheckedChange = null) },
    )
}

@Composable
private fun CancelButton(onClick: () -> Unit) {
    FinoteButton(text = stringResource(R.string.action_cancel), onClick = onClick, style = FinoteButtonStyle.Ghost)
}
