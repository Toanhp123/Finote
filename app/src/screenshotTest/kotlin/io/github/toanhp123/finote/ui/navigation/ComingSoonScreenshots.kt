package io.github.toanhp123.finote.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@PreviewTest
@ScreenPreviews
@Composable
private fun ComingSoonScreenPreview() {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            ComingSoonScreen(titleRes = R.string.tab_transactions)
        }
    }
}
