package io.github.toanhp123.finote.ui.settings.capture

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.BankSummary
import java.time.ZoneOffset

private const val PHONE = "spec:width=360dp,height=640dp"

// 2026-11-10 08:30 UTC. Every preview passes ZoneOffset.UTC, so the text does not depend on the recording machine.
private const val CHECKED_AT = 1_794_299_400_000L

@Preview(name = "Light", device = PHONE)
@Preview(name = "Dark", device = PHONE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Large font", device = PHONE, fontScale = 2f)
private annotation class ScreenPreviews

private val banks =
    listOf(
        BankSummary("a", "Vietcombank", activeRuleCount = 3, highestVersion = 2, hasUserEdits = false),
        BankSummary("b", "MB Bank", activeRuleCount = 2, highestVersion = 1, hasUserEdits = false),
        BankSummary("c", "MoMo", activeRuleCount = 5, highestVersion = 1, hasUserEdits = true),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOffNeverCheckedPreview() = Frame(NotificationCaptureSettingsState(banks = banks))

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOnCheckingPreview() =
    Frame(
        NotificationCaptureSettingsState(
            isAccessEnabled = true,
            isAutoUpdateEnabled = true,
            isChecking = true,
            banks = banks,
        ),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOnUpdatedPreview() =
    Frame(
        NotificationCaptureSettingsState(
            isAccessEnabled = true,
            isAutoUpdateEnabled = true,
            lastCheck = RuleUpdateCheck(CHECKED_AT, RuleUpdateResult.Updated(2)),
            banks = banks,
        ),
    )

@Composable
private fun Frame(state: NotificationCaptureSettingsState) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            NotificationCaptureSettingsScreen(state = state, onIntent = {}, zone = ZoneOffset.UTC)
        }
    }
}
