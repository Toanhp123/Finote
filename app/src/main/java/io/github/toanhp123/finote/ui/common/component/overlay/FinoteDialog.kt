package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import com.composeunstyled.DialogPanel
import com.composeunstyled.Scrim
import com.composeunstyled.UnstyledDialog
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A centered card over a scrim. A tap outside or Back calls [onDismiss]. The dialog owns its buttons: a ghost
 * [dismissText] button and a [confirmText] button, which is red when [isDestructive]. [content] sits between [text]
 * and the buttons, for one more control such as a checkbox row.
 */
@Composable
fun FinoteDialog(
    isVisible: Boolean,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    isDestructive: Boolean = false,
    dismissText: String = stringResource(R.string.action_cancel),
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    if (LocalInspectionMode.current) {
        if (isVisible) {
            InlineOverlay(Alignment.Center) {
                Box(modifier = modifier.safeDrawingPadding().padding(FinoteTheme.spacing.xl)) {
                    DialogCard(
                        title = title,
                        confirmText = confirmText,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                        text = text,
                        isDestructive = isDestructive,
                        dismissText = dismissText,
                        content = content,
                    )
                }
            }
        }
        return
    }
    // UnstyledDialog seeds its state from the first `visible`: start hidden so a first open runs the enter animation.
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(isVisible) { shown = isVisible }
    UnstyledDialog(
        visible = shown,
        onDismissRequest = onDismiss,
        overlay = { Scrim(enter = fadeIn(), exit = fadeOut()) },
    ) {
        Box(
            modifier = modifier.fillMaxSize().safeDrawingPadding().padding(FinoteTheme.spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            DialogPanel {
                DialogCard(
                    title = title,
                    confirmText = confirmText,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss,
                    text = text,
                    isDestructive = isDestructive,
                    dismissText = dismissText,
                    content = content,
                )
            }
        }
    }
}

@Composable
internal fun DialogCard(
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
    isDestructive: Boolean = false,
    dismissText: String = stringResource(R.string.action_cancel),
    content: (@Composable ColumnScope.() -> Unit)? = null,
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
        content?.invoke(this)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FinoteButton(text = dismissText, onClick = onDismiss, style = FinoteButtonStyle.Ghost)
            FinoteButton(
                text = confirmText,
                onClick = onConfirm,
                style = if (isDestructive) FinoteButtonStyle.Danger else FinoteButtonStyle.Primary,
            )
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
            confirmText = "Delete",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
        )
    }
}

@FinotePreviews
@Composable
private fun FinoteDialogWithContentPreview() {
    FinotePreviewSurface {
        DialogCard(
            title = "Ignore account?",
            text = "No new transactions will be recorded for this account.",
            confirmText = "Ignore",
            onConfirm = {},
            onDismiss = {},
            isDestructive = true,
            content = { Text(text = "Extra control goes here", color = FinoteTheme.scheme.onSurface) },
        )
    }
}
