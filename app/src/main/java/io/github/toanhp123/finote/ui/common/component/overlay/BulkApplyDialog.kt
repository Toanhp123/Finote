package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews

/**
 * Asks whether a saved keyword rule should also set the category of [count] past rows. It shows while [count] is
 * set. Call it at the screen root.
 */
@Composable
fun BulkApplyDialog(
    count: Int?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // The last count stays in the title while the dialog fades out.
    var lastCount by remember { mutableIntStateOf(0) }
    SideEffect { if (count != null) lastCount = count }
    val shown = count ?: lastCount
    FinoteDialog(
        isVisible = count != null,
        title = pluralStringResource(R.plurals.bulk_apply_title, shown, shown),
        confirmText = stringResource(R.string.bulk_apply_confirm),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        text = stringResource(R.string.bulk_apply_text),
    )
}

@FinotePreviews
@Composable
private fun BulkApplyDialogPreview() {
    FinotePreviewSurface {
        BulkApplyDialog(count = 12, onConfirm = {}, onDismiss = {})
    }
}
