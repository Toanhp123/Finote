package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.control.FinoteTextField
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent
import io.github.toanhp123.finote.ui.settings.accountedit.logic.labelRes

private const val MASK_LENGTH = 4

/**
 * Asks for the kind and the last four digits of one masked number. [defaultKind] is the kind that fits the account
 * type. The dialog stays composed while it fades out, so the typed digits are cleared once it is hidden.
 */
@Composable
fun AddIdentifierDialog(
    isVisible: Boolean,
    defaultKind: IdentifierKind,
    onIntent: (AccountEditIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var kind by rememberSaveable(defaultKind) { mutableStateOf(defaultKind) }
    var mask by rememberSaveable { mutableStateOf("") }
    var hasTriedToSubmit by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            mask = ""
            hasTriedToSubmit = false
        }
    }
    FinoteDialog(
        isVisible = isVisible,
        title = stringResource(R.string.account_edit_identifier_add),
        modifier = modifier,
        confirmText = stringResource(R.string.account_edit_identifier_add_confirm),
        onConfirm = {
            if (mask.length == MASK_LENGTH) {
                onIntent(AccountEditIntent.AddIdentifierSubmitted(kind, mask))
            } else {
                hasTriedToSubmit = true
            }
        },
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
                val labels = IdentifierKind.entries.associateWith { stringResource(it.labelRes()) }
                FinoteSegmented(
                    options = IdentifierKind.entries,
                    selected = kind,
                    onSelect = { kind = it },
                    label = { labels.getValue(it) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FinoteTextField(
                    value = mask,
                    onValueChange = { mask = it.filter(Char::isDigit) },
                    label = stringResource(R.string.account_edit_mask_label),
                    modifier = Modifier.fillMaxWidth(),
                    errorText =
                        if (hasTriedToSubmit && mask.length != MASK_LENGTH) {
                            stringResource(R.string.account_edit_mask_error)
                        } else {
                            null
                        },
                    maxLength = MASK_LENGTH,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
    )
}
