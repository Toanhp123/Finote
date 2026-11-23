package io.github.toanhp123.finote.ui.cashentry.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.cashentry.CashEntryIntent
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog

/** Asks before an auto-paired transfer is split back into its two rows. Call it at the screen root. */
@Composable
fun UnpairConfirmDialog(
    isVisible: Boolean,
    onIntent: (CashEntryIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.cash_entry_unpair_confirm_title),
        confirmText = stringResource(R.string.cash_entry_unpair),
        onConfirm = { onIntent(CashEntryIntent.UnpairConfirmed) },
        onDismiss = { onIntent(CashEntryIntent.UnpairDismissed) },
        text = stringResource(R.string.cash_entry_unpair_confirm_body),
    )
}
