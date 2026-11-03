package io.github.toanhp123.finote.ui.common.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A button at least as tall as a touch target. While [isLoading] it shows a spinner in place of the text,
 * keeps its size, and ignores clicks.
 */
@Composable
fun FinoteButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FinoteButtonStyle = FinoteButtonStyle.Primary,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    @DrawableRes leadingIcon: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) FinoteTheme.motion.pressedScale else 1f,
        animationSpec = FinoteTheme.motion.standard,
        label = "buttonPressedScale",
    )
    val fade = if (enabled) 1f else FinoteTheme.colors.disabledAlpha
    val palette = style.palette()
    val loadingDescription = stringResource(R.string.cd_loading)
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = fade
                }.heightIn(min = FinoteTheme.sizes.touchTarget)
                .clip(FinoteTheme.shapes.md)
                .background(palette.container)
                .border(FinoteTheme.sizes.border, palette.border, FinoteTheme.shapes.md)
                .clickable(
                    enabled = enabled && !isLoading,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { if (isLoading) contentDescription = loadingDescription }
                .padding(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        ButtonLabel(
            text = text,
            leadingIcon = leadingIcon,
            color = palette.content,
            modifier = if (isLoading) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier,
        )
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(FinoteTheme.sizes.iconSm),
                color = palette.content,
                strokeWidth = FinoteTheme.sizes.progressStroke,
            )
        }
    }
}

@Composable
private fun ButtonLabel(
    text: String,
    @DrawableRes leadingIcon: Int?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                modifier = Modifier.size(FinoteTheme.sizes.iconSm),
                tint = color,
            )
        }
        Text(
            text = text,
            color = color,
            style =
                FinoteTheme.typography.material.labelLarge
                    .copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center,
        )
    }
}

private class ButtonPalette(
    val container: Color,
    val content: Color,
    val border: Color,
)

@Composable
@ReadOnlyComposable
private fun FinoteButtonStyle.palette(): ButtonPalette {
    val scheme = FinoteTheme.scheme
    val colors = FinoteTheme.colors
    return when (this) {
        FinoteButtonStyle.Primary -> ButtonPalette(scheme.primary, scheme.onPrimary, Color.Transparent)
        FinoteButtonStyle.Secondary -> ButtonPalette(colors.card, scheme.onSurface, colors.glass.stroke)
        FinoteButtonStyle.Ghost -> ButtonPalette(Color.Transparent, scheme.primary, Color.Transparent)
        FinoteButtonStyle.Danger -> ButtonPalette(colors.expense.color, colors.expense.onColor, Color.Transparent)
    }
}

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
