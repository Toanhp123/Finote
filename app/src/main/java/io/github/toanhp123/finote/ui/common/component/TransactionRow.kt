package io.github.toanhp123.finote.ui.common.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.format.MoneyKind

/**
 * One transaction: category tile, title and subtitle on the left, amount on the right.
 * When the row is too narrow, the amount moves under the title. The title is cut with an ellipsis, never the amount.
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
    onClick: (() -> Unit)? = null,
) {
    FinoteCard(modifier = modifier, onClick = onClick) {
        FinoteListRow(
            title = title,
            subtitle = subtitle,
            leading = { CategoryIcon(iconRes = iconRes, colorArgb = colorArgb, contentDescription = null) },
            trailing = { MoneyText(amount = amount, kind = kind, shrinkToFit = true) },
        )
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
