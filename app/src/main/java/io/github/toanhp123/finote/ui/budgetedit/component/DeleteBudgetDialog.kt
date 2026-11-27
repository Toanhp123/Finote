package io.github.toanhp123.finote.ui.budgetedit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog

/** Asks before a budget is deleted for good. Call it at the screen root, beside the sheet. */
@Composable
fun DeleteBudgetDialog(
    isVisible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.budget_delete_title),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        text = stringResource(R.string.budget_delete_text),
        isDestructive = true,
    )
}
