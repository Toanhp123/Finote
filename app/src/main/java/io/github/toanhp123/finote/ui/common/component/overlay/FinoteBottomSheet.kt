package io.github.toanhp123.finote.ui.common.component.overlay

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import io.github.toanhp123.finote.ui.common.component.glass.LocalHazeState
import io.github.toanhp123.finote.ui.common.component.glass.finoteGlass
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviewSurface
import io.github.toanhp123.finote.ui.common.component.preview.FinotePreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first

/**
 * An opaque sheet that slides up over a scrim. It closes on a swipe down, a tap outside or Back, and each of them
 * calls [onDismiss]; the caller then sets [isVisible] to false. A hide the caller starts does not call [onDismiss].
 * [onDismissComplete] runs each time the sheet has settled hidden while [isVisible] is false, whoever started the hide,
 * including the first composition with [isVisible] false. It never runs in previews.
 * [content] scrolls when it is taller than the screen.
 */
@Composable
fun FinoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissComplete: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = FinoteTheme.spacing
    ModalSheet(
        isVisible = isVisible,
        onDismiss = onDismiss,
        onDismissComplete = onDismissComplete,
        modifier = modifier,
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        content = content,
    )
}

/** The sheet without the side padding, for content that is as wide as a phone screen (see [FinoteDatePicker]). */
@Composable
internal fun ModalSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onDismissComplete: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalInspectionMode.current) {
        if (isVisible) {
            CompositionLocalProvider(LocalHazeState provides null) {
                InlineOverlay(Alignment.BottomCenter) {
                    SheetSurface(
                        modifier = modifier.statusBarsPadding().padding(top = FinoteTheme.sizes.touchTarget),
                        contentPadding = contentPadding,
                        content = content,
                    )
                }
            }
        }
        return
    }
    val state =
        rememberModalBottomSheetState(
            initialDetent = if (isVisible) SheetDetent.FullyExpanded else SheetDetent.Hidden,
            animationSpec = FinoteTheme.motion.standard,
        )
    val currentOnDismissComplete by rememberUpdatedState(onDismissComplete)
    LaunchedEffect(isVisible) {
        if (isVisible) {
            state.animateTo(SheetDetent.FullyExpanded)
            return@LaunchedEffect
        }
        try {
            state.animateTo(SheetDetent.Hidden)
        } catch (_: CancellationException) {
            // The library's own dismiss or a drag interrupted our animation and finishes the hide itself. Only a
            // cancelled effect (shown again, or left the composition) is a real cancel.
            currentCoroutineContext().ensureActive()
        }
        snapshotFlow { state.currentDetent == SheetDetent.Hidden && state.isIdle }.first { it }
        currentOnDismissComplete()
    }
    // The library also calls onDismiss when the caller hides the sheet; only a user dismiss may reach the caller.
    val visible by rememberUpdatedState(isVisible)
    // The sheet has its own window, so it cannot blur the scaffold: no haze state means the opaque solidFill.
    CompositionLocalProvider(LocalHazeState provides null) {
        UnstyledModalBottomSheet(
            state = state,
            onDismiss = { if (visible) onDismiss() },
            overlay = {
                // The library starts the scrim's fade only after a swiped sheet has settled, so the dark would
                // outlast the sheet. Tying its alpha to the sheet's position makes it leave with the sheet.
                Scrim(
                    modifier =
                        Modifier.graphicsLayer {
                            alpha = state.progress(SheetDetent.Hidden, SheetDetent.FullyExpanded)
                        },
                    enter = fadeIn(),
                    exit = fadeOut(),
                )
            },
        ) {
            // The gap that keeps the panel off the status bar goes on the Sheet's own modifier: the library puts its
            // tap handler after it, so a tap in the gap falls through to the scrim and dismisses.
            Sheet(modifier = modifier.statusBarsPadding().padding(top = FinoteTheme.sizes.touchTarget)) {
                SheetSurface(contentPadding = contentPadding, content = content)
            }
        }
    }
}

@Composable
internal fun SheetSurface(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .finoteGlass(FinoteTheme.shapes.sheet)
                .navigationBarsPadding(),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = FinoteTheme.spacing.sm)
                .size(FinoteTheme.sizes.sheetHandleWidth, FinoteTheme.sizes.sheetHandleHeight)
                .clip(FinoteTheme.shapes.full)
                .background(FinoteTheme.scheme.outlineVariant),
        )
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding),
            content = content,
        )
    }
}

@FinotePreviews
@Composable
private fun FinoteBottomSheetPreview() {
    FinotePreviewSurface {
        FinoteBottomSheet(isVisible = true, onDismiss = {}) {
            Text("Sheet", style = FinoteTheme.typography.material.titleLarge, color = FinoteTheme.scheme.onSurface)
        }
    }
}
