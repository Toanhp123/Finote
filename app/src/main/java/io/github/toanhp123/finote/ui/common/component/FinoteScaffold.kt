package io.github.toanhp123.finote.ui.common.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * A screen frame. [content] fills the area under [topBar] and runs behind [bottomBar],
 * so glass in the bottom bar blurs it. [content] gets padding at the bottom that keeps its last item clear of the bar.
 */
@Composable
fun FinoteScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    snackbarState: FinoteSnackbarState? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val hazeState = rememberHazeState()
    // The bar carries its own margin on every side, which is why the margin counts twice here.
    val bottomSpace =
        FinoteTheme.sizes.navBarHeight +
            FinoteTheme.spacing.md * 2 +
            WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    CompositionLocalProvider(LocalHazeState provides hazeState) {
        Box(modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                        .background(FinoteTheme.colors.background)
                        .statusBarsPadding(),
            ) {
                topBar()
                Box(Modifier.weight(1f)) { content(PaddingValues(bottom = bottomSpace)) }
            }
            Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { bottomBar() }
            if (snackbarState != null) {
                FinoteSnackbar(
                    host = snackbarState.host,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                start = FinoteTheme.spacing.md,
                                end = FinoteTheme.spacing.md,
                                bottom = bottomSpace,
                            ),
                )
            }
        }
    }
}

@FinotePreviews
@Composable
private fun FinoteScaffoldPreview() {
    FinotePreviewSurface {
        FinoteScaffold(
            topBar = { FinoteTopBar(title = "Home") },
            bottomBar = { FinoteNavBar(items = previewNavItems(), selectedIndex = 1, onSelect = {}) },
        ) { padding ->
            Text(text = "Content", modifier = Modifier.padding(padding))
        }
    }
}
