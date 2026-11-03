package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteButtonPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteButtonStyle.entries.forEach { style ->
                FinoteButton(
                    text = style.name,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    style = style,
                    leadingIcon = R.drawable.ic_add,
                )
            }
            FinoteButton(text = "Disabled", onClick = {}, modifier = Modifier.fillMaxWidth(), enabled = false)
            FinoteButton(text = "Loading", onClick = {}, modifier = Modifier.fillMaxWidth(), isLoading = true)
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteIconButtonPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteIconButton(iconRes = R.drawable.ic_add, contentDescription = "Add", onClick = {})
            FinoteIconButton(iconRes = R.drawable.ic_add, contentDescription = "Add", onClick = {}, enabled = false)
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteTextFieldPreview() {
    FinotePreviewSurface {
        Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
            FinoteTextField(value = "", onValueChange = {}, label = "Note", placeholder = "Placeholder")
            FinoteTextField(value = "Lunch", onValueChange = {}, label = "Note", maxLength = 20)
            FinoteTextField(value = "Lunch", onValueChange = {}, label = "Note", errorText = "Required")
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteSwitchPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteSwitch(checked = false, onCheckedChange = {})
            FinoteSwitch(checked = true, onCheckedChange = {})
            FinoteSwitch(checked = false, onCheckedChange = {}, enabled = false)
            FinoteSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteChipPreview() {
    FinotePreviewSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
            FinoteChip(text = "Food", selected = true, onClick = {}, iconRes = R.drawable.ic_restaurant)
            FinoteChip(text = "Work", selected = false, onClick = {}, iconRes = R.drawable.ic_work)
            FinoteChip(text = "All", selected = false, onClick = {})
        }
    }
}

@PreviewTest
@FinotePreviews
@Composable
private fun FinoteSegmentedPreview() {
    FinotePreviewSurface {
        FinoteSegmented(
            options = listOf("Expense", "Income", "Transfer"),
            selected = "Income",
            onSelect = {},
            label = { it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
