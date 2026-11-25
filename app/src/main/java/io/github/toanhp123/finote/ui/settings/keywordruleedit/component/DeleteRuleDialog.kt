package io.github.toanhp123.finote.ui.settings.keywordruleedit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews

/** The confirm before a user rule is deleted for good. Past transactions keep the category they already have. */
@Composable
fun DeleteRuleDialog(
    isVisible: Boolean,
    keyword: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.keyword_rule_delete_title, keyword),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        text = stringResource(R.string.keyword_rule_delete_text),
        isDestructive = true,
    )
}

@FinotePreviews
@Composable
private fun DeleteRuleDialogPreview() {
    FinotePreviewSurface {
        DeleteRuleDialog(isVisible = true, keyword = "grab", onConfirm = {}, onDismiss = {})
    }
}
