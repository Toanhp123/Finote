package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent

/** Asks before the only masked number of a bank account goes. */
@Composable
fun RemoveIdentifierDialog(
    isVisible: Boolean,
    onIntent: (AccountEditIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.account_edit_remove_last_title),
        text = stringResource(R.string.account_edit_remove_last_text),
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
        confirmText = stringResource(R.string.action_delete),
        onConfirm = { onIntent(AccountEditIntent.RemoveIdentifierConfirmed) },
        isDestructive = true,
    )
}
