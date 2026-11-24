package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteSwitchRow
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditDialog
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent

@Composable
fun IgnoreDialog(
    dialog: AccountEditDialog.Ignore,
    isVisible: Boolean,
    onIntent: (AccountEditIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.accounts_ignore_dialog_title),
        text = stringResource(R.string.accounts_ignore_dialog_text),
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
        confirmText = stringResource(R.string.accounts_ignore_confirm),
        onConfirm = { onIntent(AccountEditIntent.IgnoreConfirmed) },
        isDestructive = true,
        content =
            if (dialog.trashableCount > 0) {
                {
                    TrashOption(dialog.trashableCount, dialog.shouldTrash) {
                        onIntent(AccountEditIntent.TrashToggled(it))
                    }
                }
            } else {
                null
            },
    )
}

@Composable
private fun TrashOption(
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
