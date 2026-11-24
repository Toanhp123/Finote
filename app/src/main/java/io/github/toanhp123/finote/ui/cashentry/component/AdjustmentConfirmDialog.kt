package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog

/** Asks before an inbox item is booked that a balance adjustment may include. Call it at the screen root. */
@Composable
fun AdjustmentConfirmDialog(
    isVisible: Boolean,
    onIntent: (CashEntryIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.cash_entry_adjust_title),
        confirmText = stringResource(R.string.cash_entry_save),
        onConfirm = { onIntent(CashEntryIntent.AdjustmentConfirmed) },
        onDismiss = { onIntent(CashEntryIntent.AdjustmentDismissed) },
        text = stringResource(R.string.cash_entry_adjust_text),
    )
}
