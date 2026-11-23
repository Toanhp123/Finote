package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog

/** Asks before the stored transaction goes to the trash. Call it at the screen root, beside the sheet. */
@Composable
fun DeleteConfirmDialog(
    isVisible: Boolean,
    onIntent: (CashEntryIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.cash_entry_delete_title),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = { onIntent(CashEntryIntent.DeleteConfirmed) },
        onDismiss = { onIntent(CashEntryIntent.DeleteDismissed) },
        text = stringResource(R.string.cash_entry_delete_text),
        isDestructive = true,
    )
}
