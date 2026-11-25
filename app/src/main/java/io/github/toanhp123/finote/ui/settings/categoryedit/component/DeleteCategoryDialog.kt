package io.github.toanhp123.finote.ui.settings.categoryedit.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews

/** The plain confirm for a category no transaction uses. With [hasChildren] it says the subcategories go too. */
@Composable
fun DeleteCategoryDialog(
    isVisible: Boolean,
    name: String,
    hasChildren: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val empty = stringResource(R.string.category_delete_no_transactions)
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.category_delete_title, name),
        confirmText = stringResource(R.string.action_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
        text = if (hasChildren) "$empty ${stringResource(R.string.category_delete_branch_note)}" else empty,
        isDestructive = true,
    )
}

@FinotePreviews
@Composable
private fun DeleteCategoryDialogPreview() {
    FinotePreviewSurface {
        DeleteCategoryDialog(isVisible = true, name = "Shopping", hasChildren = true, onConfirm = {}, onDismiss = {})
    }
}
