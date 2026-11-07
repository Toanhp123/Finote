package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A centered card over a scrim. A tap outside or Back calls [onDismiss]. [confirm] and [dismiss] are the buttons
 * at the bottom end, usually [FinoteButton]s.
 */
@Composable
fun FinoteDialog(
    isVisible: Boolean,
    title: String,
    onDismiss: () -> Unit,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismiss: (@Composable () -> Unit)? = null,
) {
    UnstyledDialog(
        visible = isVisible,
        onDismissRequest = onDismiss,
        overlay = { Scrim(enter = fadeIn(), exit = fadeOut()) },
    ) {
        Box(
            modifier = modifier.fillMaxSize().safeDrawingPadding().padding(FinoteTheme.spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            DialogPanel {
                DialogCard(title = title, text = text, confirm = confirm, dismiss = dismiss)
            }
        }
    }
}

@Composable
internal fun DialogCard(
    title: String,
    confirm: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    dismiss: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier =
            modifier
                .widthIn(max = FinoteTheme.sizes.dialogMaxWidth)
                .fillMaxWidth()
                .clip(FinoteTheme.shapes.xl)
                .background(FinoteTheme.colors.card)
                .padding(FinoteTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md),
    ) {
        Text(text = title, style = FinoteTheme.typography.material.titleLarge, color = FinoteTheme.scheme.onSurface)
        if (text != null) {
            Text(
                text = text,
                style = FinoteTheme.typography.material.bodyMedium,
                color = FinoteTheme.scheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dismiss?.invoke()
            confirm()
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteDialogPreview() {
    FinotePreviewSurface {
        FinoteDialog(
            isVisible = true,
            title = "Delete transaction?",
            text = "It moves to the trash and can be restored for 30 days.",
            onDismiss = {},
            dismiss = { FinoteButton(text = "Cancel", onClick = {}, style = FinoteButtonStyle.Ghost) },
            confirm = { FinoteButton(text = "Delete", onClick = {}, style = FinoteButtonStyle.Danger) },
        )
    }
}
