package io.github.toanhp123.finote.ui.common.component

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.composeunstyled.Scrim
import com.composeunstyled.Sheet
import com.composeunstyled.SheetDetent
import com.composeunstyled.UnstyledModalBottomSheet
import com.composeunstyled.rememberModalBottomSheetState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * An opaque sheet that slides up over a scrim. It closes on a swipe down, a tap outside or Back, and each of them
 * calls [onDismiss]; the caller then sets [isVisible] to false. A hide the caller starts does not call [onDismiss].
 * [content] scrolls when it is taller than the screen.
 */
@Composable
fun FinoteBottomSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = FinoteTheme.spacing
    ModalSheet(
        isVisible = isVisible,
        onDismiss = onDismiss,
        modifier = modifier,
        contentPadding = PaddingValues(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        content = content,
    )
}

/** The sheet without the side padding, for content such as the date picker that is as wide as a phone screen. */
@Composable
internal fun ModalSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val state =
        rememberModalBottomSheetState(
            initialDetent = if (isVisible) SheetDetent.FullyExpanded else SheetDetent.Hidden,
            animationSpec = FinoteTheme.motion.emphasized,
        )
    LaunchedEffect(isVisible) {
        state.animateTo(if (isVisible) SheetDetent.FullyExpanded else SheetDetent.Hidden)
    }
    // The library also calls onDismiss when the caller hides the sheet; only a user dismiss may reach the caller.
    val visible by rememberUpdatedState(isVisible)
    // The sheet has its own window, so it cannot blur the scaffold: no haze state means the opaque solidFill.
    CompositionLocalProvider(LocalHazeState provides null) {
        UnstyledModalBottomSheet(
            state = state,
            onDismiss = { if (visible) onDismiss() },
            overlay = { Scrim(enter = fadeIn(), exit = fadeOut()) },
        ) {
            Sheet(modifier = modifier) { SheetSurface(contentPadding = contentPadding, content = content) }
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
