package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.format.MoneyKind

// 16 characters five times: an 80-character title.
private const val LONG_TITLE_REPEAT = 5

@PreviewTest
@FinotePreviews
@Composable
private fun MoneyTextPreview() {
    FinotePreviewSurface {
        Column {
            MoneyText(amount = 45_000, kind = MoneyKind.Expense)
            MoneyText(amount = 15_000_000, kind = MoneyKind.Income)
            MoneyText(amount = 2_000_000, kind = MoneyKind.Transfer)
            MoneyText(amount = 12_450_000, kind = MoneyKind.Neutral)
        }
    }
}

@PreviewTest
@Preview(name = "English", locale = "en")
@Composable
private fun MoneyTextEnglishPreview() {
    FinotePreviewSurface { MoneyText(amount = 1_234_567, kind = MoneyKind.Neutral) }
}

@PreviewTest
@Preview(name = "Vietnamese", locale = "vi")
@Composable
private fun MoneyTextVietnamesePreview() {
    FinotePreviewSurface {
        Column {
            MoneyText(amount = 1_234_567, kind = MoneyKind.Neutral)
            MoneyText(amount = 45_000, kind = MoneyKind.Expense)
            MoneyText(amount = 15_000_000, kind = MoneyKind.Income)
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun CategoryIconPreview() {
    FinotePreviewSurface {
        Column {
            CategoryIcon(iconRes = R.drawable.ic_restaurant, colorArgb = PreviewRedArgb, contentDescription = null)
            CategoryIcon(iconRes = R.drawable.ic_work, colorArgb = PreviewGreenArgb, contentDescription = null)
            CategoryIcon(
                iconRes = R.drawable.ic_local_gas_station,
                colorArgb = PreviewRedArgb,
                contentDescription = null,
            )
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteCardPreview() {
    FinotePreviewSurface {
        Column {
            FinoteCard { Text(text = "Card") }
            FinoteCard(onClick = {}) { Text(text = "Clickable card") }
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteListRowPreview() {
    FinotePreviewSurface {
        Column {
            FinoteListRow(title = "Title only")
            FinoteListRow(
                title = "Title, subtitle and trailing",
                subtitle = "Subtitle",
                leading = {
                    CategoryIcon(
                        iconRes = R.drawable.ic_restaurant,
                        colorArgb = PreviewRedArgb,
                        contentDescription = null,
                    )
                },
                trailing = { MoneyText(amount = 45_000, kind = MoneyKind.Expense) },
            )
        }
    }
}

@PreviewTest
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
            TransactionRow(
                title = "Fuel",
                subtitle = "Card",
                amount = 120_000,
                kind = MoneyKind.Transfer,
                iconRes = R.drawable.ic_local_gas_station,
                colorArgb = PreviewRedArgb,
            )
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun TransactionRowLongPreview() {
    FinotePreviewSurface {
        TransactionRow(
            title = "Very long title ".repeat(LONG_TITLE_REPEAT),
            subtitle = "Cash",
            amount = Long.MAX_VALUE,
            kind = MoneyKind.Expense,
            iconRes = R.drawable.ic_restaurant,
            colorArgb = PreviewRedArgb,
        )
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun BalanceHeroCardPreview() {
    FinotePreviewSurface {
        BalanceHeroCard(
            label = stringResource(R.string.balance_total),
            balance = 12_450_000,
            income = 15_000_000,
            expense = 2_100_000,
        )
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun BalanceHeroCardNegativePreview() {
    FinotePreviewSurface {
        BalanceHeroCard(
            label = stringResource(R.string.balance_total),
            balance = -2_500_000,
            income = 1_000_000,
            expense = 3_500_000,
        )
    }
}
