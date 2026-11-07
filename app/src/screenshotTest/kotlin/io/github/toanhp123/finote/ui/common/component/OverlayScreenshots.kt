package io.github.toanhp123.finote.ui.common.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.finance.TransactionRow
import io.github.toanhp123.finote.ui.common.component.layout.FinoteScaffold
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.DatePickerContent
import io.github.toanhp123.finote.ui.common.component.overlay.DialogCard
import io.github.toanhp123.finote.ui.common.component.overlay.SheetSurface
import io.github.toanhp123.finote.ui.common.component.preview.PreviewGreenArgb
import io.github.toanhp123.finote.ui.common.component.preview.PreviewRedArgb
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import java.time.LocalDate

private const val PHONE = "spec:width=360dp,height=640dp"
private const val SCRIM_ALPHA = 0.6f

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinoteBottomSheetOpenPreview() {
    ScreenBehind(Alignment.BottomCenter) {
        SheetSurface(contentPadding = PaddingValues(FinoteTheme.spacing.lg)) {
            Text(
                text = "Sheet content",
                style = FinoteTheme.typography.material.titleLarge,
                color = FinoteTheme.scheme.onSurface,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
@Composable
private fun FinoteDialogOpenPreview() {
    ScreenBehind(Alignment.Center) {
        DialogCard(
            title = "Delete transaction?",
            text = "It moves to the trash and can be restored for 30 days.",
            dismiss = { FinoteButton(text = "Cancel", onClick = {}, style = FinoteButtonStyle.Ghost) },
            confirm = { FinoteButton(text = "Delete", onClick = {}, style = FinoteButtonStyle.Danger) },
            modifier = Modifier.padding(FinoteTheme.spacing.xl),
        )
    }
}

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FinoteDatePickerOpenPreview() {
    ScreenBehind(Alignment.BottomCenter) {
        SheetSurface {
            // A past month: the picker rings "today" from the system clock, so a current month breaks the image daily.
            DatePickerContent(selected = LocalDate.of(2025, 3, 3), onConfirm = {}, onDismiss = {})
        }
    }
}

/** layoutlib has no second window: the sheet and the dialog are drawn as their content over the scrim, without blur. */
@Composable
private fun ScreenBehind(
    alignment: Alignment,
    overlay: @Composable () -> Unit,
) {
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
            Box(
                modifier = Modifier.fillMaxSize().background(FinoteTheme.scheme.scrim.copy(alpha = SCRIM_ALPHA)),
                contentAlignment = alignment,
            ) { overlay() }
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
