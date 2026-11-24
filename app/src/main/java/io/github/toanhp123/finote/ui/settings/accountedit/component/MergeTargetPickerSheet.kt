package io.github.toanhp123.finote.ui.settings.accountedit.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditDialog
import io.github.toanhp123.finote.ui.settings.accountedit.AccountEditIntent

/** The accounts the edited one could merge into. A null [picker] hides the sheet; it keeps its rows while it leaves. */
@Composable
fun MergeTargetPickerSheet(
    picker: AccountEditDialog.MergeTargetPicker?,
    onIntent: (AccountEditIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = rememberLastNonNull(picker)
    FinoteBottomSheet(
        isVisible = picker != null,
        modifier = modifier,
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
    ) {
        Text(
            text = stringResource(R.string.accounts_merge_into),
            modifier = Modifier.padding(bottom = FinoteTheme.spacing.sm).semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        shown?.targets?.forEach { target ->
            FinoteListRow(
                title = displayName(target.name, target.nameKey),
                trailing = { MoneyText(amount = target.currentBalance, kind = MoneyKind.Neutral) },
                onClick = { onIntent(AccountEditIntent.MergeTargetPicked(target.id)) },
            )
        }
    }
}
