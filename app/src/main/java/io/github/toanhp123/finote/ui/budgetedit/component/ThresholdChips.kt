package io.github.toanhp123.finote.ui.budgetedit.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.service.BudgetRules
import io.github.toanhp123.finote.ui.common.component.control.FinoteChip
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** The alert marks as chips, one selected. A large font wraps the row instead of clipping a chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThresholdChips(
    selected: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs)) {
        Text(
            text = stringResource(R.string.budget_edit_threshold),
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            BudgetRules.THRESHOLDS.forEach { mark ->
                FinoteChip(
                    text = stringResource(R.string.overview_share, mark),
                    selected = mark == selected,
                    onClick = { onPick(mark) },
                )
            }
        }
    }
}
