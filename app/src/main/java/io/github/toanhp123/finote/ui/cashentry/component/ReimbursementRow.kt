package io.github.toanhp123.finote.ui.cashentry.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteSegmented
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** The reimbursement choice of an expense: a small label over the segmented control. */
@Composable
internal fun ReimbursementRow(
    selected: ReimbursementChoice,
    onPick: (ReimbursementChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val labels = ReimbursementChoice.entries.associateWith { stringResource(it.labelRes()) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.reimbursement_title),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelLarge,
        )
        FinoteSegmented(
            options = ReimbursementChoice.entries,
            selected = selected,
            onSelect = onPick,
            label = { labels.getValue(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@StringRes
private fun ReimbursementChoice.labelRes(): Int =
    when (this) {
        ReimbursementChoice.NORMAL -> R.string.reimbursement_normal
        ReimbursementChoice.PENDING -> R.string.reimbursement_pending
        ReimbursementChoice.SETTLED -> R.string.reimbursement_settled
    }
