package io.github.toanhp123.finote.ui.budget.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.budget.model.BudgetStatus
import io.github.toanhp123.finote.ui.common.component.finance.CategoryIcon
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

/** The total budget on top of the list. It adds the per-day figure, which only the whole month has. */
@Composable
fun BudgetTotalCard(
    status: BudgetStatus,
    today: LocalDate,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    val primary = FinoteTheme.scheme.primary.toArgb()
    BudgetRow(
        status = status,
        title = stringResource(R.string.budget_total_title),
        onClick = onClick,
        modifier = modifier,
        leading = { CategoryIcon(iconRes = R.drawable.ic_pie_chart, colorArgb = primary, contentDescription = null) },
        today = today,
        onEdit = onEdit,
    )
}
