package io.github.toanhp123.finote.ui.transactions.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** [selected] is null while no row is open; the sheet then keeps the last choice it showed while it slides out. */
@Composable
fun ReimbursementSheet(
    isVisible: Boolean,
    selected: ReimbursementChoice?,
    hasSaveError: Boolean,
    onPick: (ReimbursementChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var lastShown by remember { mutableStateOf(ReimbursementChoice.NORMAL) }
    LaunchedEffect(selected) { selected?.let { lastShown = it } }
    FinoteBottomSheet(isVisible = isVisible, onDismiss = onDismiss) {
        ReimbursementSheetContent(selected = selected ?: lastShown, hasSaveError = hasSaveError, onPick = onPick)
    }
}

/** The content inside the sheet. It has no state of its own, so previews and screenshot tests can draw it. */
@Composable
fun ReimbursementSheetContent(
    selected: ReimbursementChoice,
    hasSaveError: Boolean,
    onPick: (ReimbursementChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = ReimbursementChoice.entries.associateWith { stringResource(it.labelRes()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
        Text(
            text = stringResource(R.string.reimbursement_title),
            modifier = Modifier.semantics { heading() },
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.titleLarge,
        )
        FinoteSegmented(
            options = ReimbursementChoice.entries,
            selected = selected,
            onSelect = onPick,
            label = { labels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasSaveError) {
            Text(
                text = stringResource(R.string.reimbursement_save_error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = FinoteTheme.colors.expense.color,
                style = FinoteTheme.typography.material.bodySmall,
            )
        }
    }
}

@StringRes
private fun ReimbursementChoice.labelRes(): Int =
    when (this) {
        ReimbursementChoice.NORMAL -> R.string.reimbursement_normal
        ReimbursementChoice.PENDING -> R.string.reimbursement_pending
        ReimbursementChoice.SETTLED -> R.string.reimbursement_settled
    }
