package io.github.toanhp123.finote.ui.common.component.finance

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * One transaction: category tile, title and subtitle on the left, amount on the right.
 * When the row is too narrow, the amount moves under the title. The title is cut with an ellipsis, never the amount.
 * A [fee] above zero adds a second line under the amount. [isMuted] dims the row, for one left out of reports.
 */
@Composable
fun TransactionRow(
    title: String,
    subtitle: String,
    amount: Long,
    kind: MoneyKind,
    @DrawableRes iconRes: Int,
    colorArgb: Int,
    modifier: Modifier = Modifier,
    fee: Long = 0,
    isMuted: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    FinoteCard(modifier = modifier, onClick = onClick) {
        FinoteListRow(
            // The alpha sits on the row, not the card, so the card background stays solid.
            modifier = if (isMuted) Modifier.alpha(FinoteTheme.colors.mutedAlpha) else Modifier,
            title = title,
            subtitle = subtitle,
            leading = { CategoryIcon(iconRes = iconRes, colorArgb = colorArgb, contentDescription = null) },
            trailing = { RowAmount(amount, kind, fee) },
        )
    }
}

@Composable
private fun RowAmount(
    amount: Long,
    kind: MoneyKind,
    fee: Long,
) {
    if (fee <= 0) {
        MoneyText(amount = amount, kind = kind, shrinkToFit = true)
        return
    }
    Column(horizontalAlignment = Alignment.End) {
        MoneyText(amount = amount, kind = kind, shrinkToFit = true)
        Row(
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.transaction_row_fee),
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
            )
            MoneyText(amount = fee, kind = MoneyKind.Expense, shrinkToFit = true)
        }
    }
}

@FinotePreviews
@Composable
private fun TransactionRowPreview() {
    FinotePreviewSurface {
        Column {
            TransactionRow(
                title = "Food",
                subtitle = "Cash",
                amount = 45_000,
                kind = MoneyKind.Expense,
                iconRes = R.drawable.ic_restaurant,
                colorArgb = PreviewRedArgb,
            )
            TransactionRow(
                title = "Salary",
                subtitle = "Bank",
                amount = 15_000_000,
                kind = MoneyKind.Income,
                iconRes = R.drawable.ic_work,
                colorArgb = PreviewGreenArgb,
                onClick = {},
            )
        }
    }
}
