package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A text field with its [label] above. A non-null [errorText] turns the border red and shows the text below.
 * With [maxLength] the field refuses longer input and shows a counter.
 */
@Composable
fun FinoteTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    errorText: String? = null,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor =
        when {
            errorText != null -> FinoteTheme.colors.expense.color
            isFocused -> FinoteTheme.scheme.primary
            else -> FinoteTheme.colors.glass.stroke
        }
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { if (errorText != null) error(errorText) },
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xs),
    ) {
        Text(
            text = label,
            color = FinoteTheme.scheme.onSurfaceVariant,
            style = FinoteTheme.typography.material.labelMedium,
        )
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(if (maxLength == null) it else it.take(maxLength)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle =
                FinoteTheme.typography.material.bodyLarge
                    .copy(color = FinoteTheme.scheme.onSurface),
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(FinoteTheme.scheme.primary),
            decorationBox = { innerTextField ->
                FieldBox(borderColor = borderColor) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            color = FinoteTheme.scheme.onSurfaceVariant,
                            style = FinoteTheme.typography.material.bodyLarge,
                        )
                    }
                    innerTextField()
                }
            },
        )
        FieldFooter(errorText = errorText, counter = maxLength?.let { "${value.length}/$it" })
    }
}

@Composable
private fun FieldBox(
    borderColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.md)
                .background(FinoteTheme.colors.card)
                .border(FinoteTheme.sizes.border, borderColor, FinoteTheme.shapes.md)
                .padding(horizontal = FinoteTheme.spacing.md, vertical = FinoteTheme.spacing.sm),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun FieldFooter(
    errorText: String?,
    counter: String?,
) {
    if (errorText == null && counter == null) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm)) {
        Text(
            text = errorText.orEmpty(),
            modifier = Modifier.weight(1f),
            color = FinoteTheme.colors.expense.color,
            style = FinoteTheme.typography.material.bodySmall,
        )
        if (counter != null) {
            Text(
                text = counter,
                color = FinoteTheme.scheme.onSurfaceVariant,
                style = FinoteTheme.typography.material.bodySmall,
            )
        }
    }
}

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
