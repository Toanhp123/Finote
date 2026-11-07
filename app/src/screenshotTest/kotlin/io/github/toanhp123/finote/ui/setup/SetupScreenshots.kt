package io.github.toanhp123.finote.ui.setup

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

private const val PHONE = "spec:width=360dp,height=640dp"

@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
private annotation class ScreenPreviews

@PreviewTest
@ScreenPreviews
@Composable
private fun SetupIntroPreview() = Frame(SetupState())

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun SetupRetryPreview() = Frame(SetupState(step = SetupStep.RETRY, hasLaunchedSettings = true))

@PreviewTest
@Preview(name = "Light", device = PHONE)
@Composable
private fun SetupGrantedPreview() = Frame(SetupState(step = SetupStep.GRANTED, hasLaunchedSettings = true))

@Composable
private fun Frame(state: SetupState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            SetupScreen(state = state, onIntent = {})
        }
    }
}
