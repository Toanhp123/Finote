package io.github.toanhp123.finote.ui.transactions.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.transactions.TransactionListIntent

/** Asks before a balance adjustment goes to the trash. Call it at the screen root, beside the list. */
@Composable
fun DeleteAdjustmentDialog(
    isVisible: Boolean,
    onIntent: (TransactionListIntent) -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.transactions_delete_adjustment_title),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = { onIntent(TransactionListIntent.DeleteConfirmed) },
        onDismiss = { onIntent(TransactionListIntent.DeleteDismissed) },
        text = stringResource(R.string.transactions_delete_adjustment_text),
        isDestructive = true,
    )
}
