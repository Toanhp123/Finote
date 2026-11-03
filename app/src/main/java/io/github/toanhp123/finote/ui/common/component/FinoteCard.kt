package io.github.toanhp123.finote.ui.common.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** A rounded surface. With [onClick] it shrinks a little while pressed. */
@Composable
fun FinoteCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) FinoteTheme.motion.pressedScale else 1f,
        animationSpec = FinoteTheme.motion.standard,
        label = "cardPressedScale",
    )
    val press =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
        }
    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(FinoteTheme.shapes.lg)
                .background(FinoteTheme.colors.card)
                .then(press)
                .padding(FinoteTheme.spacing.md),
        content = content,
    )
}

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
