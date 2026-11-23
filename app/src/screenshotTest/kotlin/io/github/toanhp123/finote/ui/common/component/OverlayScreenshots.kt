package io.github.toanhp123.finote.ui.common.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.PHONE
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.component.finance.TransactionRow
import io.github.toanhp123.finote.ui.common.component.layout.FinoteScaffold
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDatePicker
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteDialog
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinoteBottomSheetOpenPreview() {
    ScreenBehind {
        FinoteBottomSheet(isVisible = true, onDismiss = {}) {
            Text(
                text = "Sheet content",
                style = FinoteTheme.typography.material.titleLarge,
                color = FinoteTheme.scheme.onSurface,
            )
        }
    }
}

@PreviewTest
@ScreenPreviews
@Composable
private fun FinoteDialogOpenPreview() {
    ScreenBehind {
        FinoteDialog(
            isVisible = true,
            title = "Delete transaction?",
            text = "It moves to the trash and can be restored for 30 days.",
            confirmText = "Delete",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
            dismissText = "Cancel",
        )
    }
}

@PreviewTest
@ScreenPreviews
@Composable
private fun FinoteDialogWithContentOpenPreview() {
    ScreenBehind {
        FinoteDialog(
            isVisible = true,
            title = "Ignore account?",
            text = "No new transactions will be recorded for this account.",
            confirmText = "Ignore",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
            dismissText = "Cancel",
            content = { Text(text = "Extra control goes here", color = FinoteTheme.scheme.onSurface) },
        )
    }
}

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinoteDatePickerOpenPreview() {
    ScreenBehind {
        // A past month: the picker rings "today" from the system clock, so a current month breaks the image daily.
        FinoteDatePicker(isVisible = true, selected = LocalDate.of(2025, 3, 3), onConfirm = {}, onDismiss = {})
    }
}

/** The screen the overlay opens over. The overlay is called last, in the same Box, so it covers the whole body. */
@Composable
private fun ScreenBehind(overlay: @Composable () -> Unit) {
    FinoteTheme(dynamicColor = false) {
        Box {
            FinoteScaffold(topBar = { FinoteTopBar(title = "Finote") }) { padding ->
                Column(
                    modifier = Modifier.padding(padding).padding(horizontal = FinoteTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
                ) {
                    BackdropRow("Food", 45_000, MoneyKind.Expense)
                    BackdropRow("Salary", 15_000_000, MoneyKind.Income)
                    BackdropRow("Fuel", 120_000, MoneyKind.Expense)
                }
            }
            overlay()
        }
    }
}

@Composable
private fun BackdropRow(
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
