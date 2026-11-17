package io.github.toanhp123.finote.ui.settings.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.android.tools.screenshot.PreviewTest
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.ui.ScreenPreviews
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.logic.BankSummary
import java.time.ZoneOffset

// 2026-11-10 08:30 UTC. Every preview passes ZoneOffset.UTC, so the text does not depend on the recording machine.
private const val CHECKED_AT = 1_794_299_400_000L

private val banks =
    listOf(
        BankSummary("a", "Vietcombank", activeRuleCount = 3, highestVersion = 2, hasUserEdits = false),
        BankSummary("b", "MB Bank", activeRuleCount = 2, highestVersion = 1, hasUserEdits = false),
        BankSummary("c", "MoMo", activeRuleCount = 5, highestVersion = 1, hasUserEdits = true),
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOffNeverCheckedPreview() =
    Frame(NotificationCaptureSettingsState(banks = banks), isAccessEnabled = false)

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOnCheckingPreview() =
    Frame(
        NotificationCaptureSettingsState(
            isAutoUpdateEnabled = true,
            isChecking = true,
            banks = banks,
        ),
        isAccessEnabled = true,
    )

@PreviewTest
@ScreenPreviews
@Composable
private fun CaptureOnUpdatedPreview() =
    Frame(
        NotificationCaptureSettingsState(
            isAutoUpdateEnabled = true,
            lastCheck = RuleUpdateCheck(CHECKED_AT, RuleUpdateResult.Updated(2)),
            banks = banks,
        ),
        isAccessEnabled = true,
    )

@Composable
private fun Frame(
    state: NotificationCaptureSettingsState,
    isAccessEnabled: Boolean,
) {
    FinoteTheme(dynamicColor = false) {
        Box(Modifier.fillMaxSize().background(FinoteTheme.colors.background).statusBarsPadding()) {
            NotificationCaptureSettingsScreen(
                state = state,
                isAccessEnabled = isAccessEnabled,
                onIntent = {},
                zone = ZoneOffset.UTC,
            )
        }
    }
}
