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
private fun SettingsAccessOnPreview() = Frame(SettingsState(bankCount = 4), isAccessEnabled = true)

@PreviewTest
@ScreenPreviews
@Composable
private fun SettingsAccessOffPreview() = Frame(SettingsState(bankCount = 4), isAccessEnabled = false)

@PreviewTest
@ScreenPreviews
@Composable
private fun SettingsInboxUnseenPreview() =
    Frame(SettingsState(bankCount = 4, inboxCount = 3, hasUnseenInbox = true), isAccessEnabled = true)

@Composable
private fun Frame(
    state: SettingsState,
    isAccessEnabled: Boolean,
) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            SettingsScreen(state = state, isAccessEnabled = isAccessEnabled, onIntent = {})
        }
    }
}
