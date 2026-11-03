package io.github.toanhp123.finote.ui.common.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val PHONE = "spec:width=360dp,height=640dp"
private const val SKELETON_HALFWAY = 0.5f
private const val EXTRA_ROWS = 6

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteTopBarPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteTopBar(title = "Transactions")
            FinoteTopBar(
                title = "A very long title that has to be cut with an ellipsis",
                onBack = {},
                actions = {
                    FinoteIconButton(iconRes = R.drawable.ic_settings, contentDescription = "Settings", onClick = {})
                },
            )
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteNavBarPreview() {
    FinotePreviewSurface {
        Column {
            FinoteNavBar(items = previewNavItems(), selectedIndex = 0, onSelect = {})
            FinoteNavBar(items = previewNavItems(), selectedIndex = 2, onSelect = {})
        }
    }
}

/** Blur off, the bar over a row: the icons must stay readable on the solid fill. */
@PreviewTest
@FinotePreviews
@Composable
private fun FinoteNavBarNoBlurPreview() {
    CompositionLocalProvider(LocalBlurEnabled provides false) {
        FinotePreviewSurface {
            Box(contentAlignment = Alignment.Center) {
                SampleRow("Lunch", 45_000, MoneyKind.Expense)
                FinoteNavBar(items = previewNavItems(), selectedIndex = 1, onSelect = {})
            }
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteSnackbarPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            SnackbarCard(message = "Transaction deleted", actionLabel = "Undo", onAction = {})
            SnackbarCard(message = "Saved", actionLabel = null, onAction = {})
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteEmptyStatePreview() {
    FinotePreviewSurface {
        Column {
            FinoteEmptyState(
                iconRes = R.drawable.ic_receipt_long,
                title = "No transactions yet",
                message = "Add one or let Finote read your bank notifications.",
                action = { FinoteButton(text = "Add", onClick = {}, leadingIcon = R.drawable.ic_add) },
            )
            FinoteEmptyState(iconRes = R.drawable.ic_receipt_long, title = "Nothing here")
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteSkeletonPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            SkeletonSurface(progress = { 0f }, modifier = Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget))
            SkeletonSurface(
                progress = { SKELETON_HALFWAY },
                modifier = Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget),
            )
            SkeletonSurface(
                progress = { SKELETON_HALFWAY },
                modifier = Modifier.size(FinoteTheme.sizes.avatar),
                shape = FinoteTheme.shapes.full,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun FinoteScreenLightPreview() {
    SampleScreen()
}

@PreviewTest
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinoteScreenDarkPreview() {
    SampleScreen()
}

/** Rows run behind the bar. With blur they are smeared there, without it the solid fill hides them. */
@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun FinoteScreenBehindBarPreview() {
    SampleScreen(extraRows = EXTRA_ROWS)
}

/** The home screen of mockup C: top bar, hero, rows, floating nav bar. */
@Composable
private fun SampleScreen(extraRows: Int = 0) {
    FinoteTheme(dynamicColor = false) {
        FinoteScaffold(
            topBar = { FinoteTopBar(title = "Finote") },
            bottomBar = { FinoteNavBar(items = previewNavItems(), selectedIndex = 0, onSelect = {}) },
        ) { padding ->
            Column(
                modifier =
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(padding)
                        .padding(horizontal = FinoteTheme.spacing.lg),
                verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
            ) {
                BalanceHeroCard(label = "Total balance", balance = 12_450_000, income = 15_000_000, expense = 2_100_000)
                SampleRow("Food", 45_000, MoneyKind.Expense)
                SampleRow("Salary", 15_000_000, MoneyKind.Income)
                SampleRow("Fuel", 120_000, MoneyKind.Expense)
                repeat(extraRows) { SampleRow("Shopping", 80_000L + it, MoneyKind.Expense) }
            }
        }
    }
}

@Composable
private fun SampleRow(
    title: String,
    amount: Long,
    kind: MoneyKind,
) {
    TransactionRow(
        title = title,
        subtitle = "Cash",
        amount = amount,
        kind = kind,
        iconRes = if (kind == MoneyKind.Income) R.drawable.ic_work else R.drawable.ic_restaurant,
        colorArgb = if (kind == MoneyKind.Income) PreviewGreenArgb else PreviewRedArgb,
    )
}
