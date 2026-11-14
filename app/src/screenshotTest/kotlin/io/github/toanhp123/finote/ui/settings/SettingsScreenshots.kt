package io.github.toanhp123.finote.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

@PreviewTest
@ScreenPreviews
@Composable
private fun SettingsAccessOnPreview() = Frame(SettingsState(isAccessEnabled = true, bankCount = 4))

@PreviewTest
@ScreenPreviews
@Composable
private fun SettingsAccessOffPreview() = Frame(SettingsState(isAccessEnabled = false, bankCount = 4))

@Composable
private fun Frame(state: SettingsState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            SettingsScreen(state = state, onIntent = {})
        }
    }
}
