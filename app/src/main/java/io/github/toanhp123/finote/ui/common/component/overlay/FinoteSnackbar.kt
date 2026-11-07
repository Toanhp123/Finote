package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import io.github.toanhp123.finote.ui.common.component.glass.finoteGlass
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/** Draws the snackbar of [host] as a glass card. [FinoteScaffold] places it above the bottom bar. */
@Composable
internal fun FinoteSnackbar(
    host: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = host, modifier = modifier) { data ->
        SnackbarCard(
            message = data.visuals.message,
            actionLabel = data.visuals.actionLabel,
            onAction = data::performAction,
        )
    }
}

/** Shows one snackbar at a time in the [FinoteScaffold] it is passed to. Later messages wait their turn. */
class FinoteSnackbarState internal constructor(
    internal val host: SnackbarHostState,
) {
    /** Suspends until the snackbar goes away. Returns true when the user pressed the action. */
    suspend fun show(
        message: String,
        actionLabel: String? = null,
    ): Boolean = host.showSnackbar(message = message, actionLabel = actionLabel) == SnackbarResult.ActionPerformed
}

@Composable
fun rememberFinoteSnackbarState(): FinoteSnackbarState = remember { FinoteSnackbarState(SnackbarHostState()) }

@Composable
internal fun SnackbarCard(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinoteTheme.spacing
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .finoteGlass(FinoteTheme.shapes.md)
                .padding(horizontal = spacing.lg, vertical = spacing.sm)
                .defaultMinSize(minHeight = FinoteTheme.sizes.navItemHeight),
        horizontalArrangement = Arrangement.spacedBy(spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = FinoteTheme.scheme.onSurface,
            style = FinoteTheme.typography.material.bodyMedium,
        )
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                modifier =
                    Modifier
                        .clickable(role = Role.Button, onClick = onAction)
                        .defaultMinSize(minHeight = FinoteTheme.sizes.touchTarget)
                        .wrapContentSize(Alignment.Center),
                color = FinoteTheme.scheme.primary,
                style = FinoteTheme.typography.material.labelLarge,
            )
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteSnackbarPreview() {
    FinotePreviewSurface {
        SnackbarCard(message = "Transaction deleted", actionLabel = "Undo", onAction = {})
    }
}
