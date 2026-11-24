package io.github.toanhp123.finote.ui.settings.capture

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.ui.settings.logic.BankSummary

/**
 * [isChecking] is true while an update runs. [lastCheck] is null before the first one.
 * [banks] lists the supported banks, read only. [adKeywords] are the taught ad keywords by package name.
 */
data class NotificationCaptureSettingsState(
    val isAutoUpdateEnabled: Boolean = false,
    val isChecking: Boolean = false,
    val lastCheck: RuleUpdateCheck? = null,
    val banks: List<BankSummary> = emptyList(),
    val adKeywords: Map<String, List<UserIgnoreKeyword>> = emptyMap(),
)

sealed interface NotificationCaptureSettingsIntent {
    data object AccessClicked : NotificationCaptureSettingsIntent

    data class AutoUpdateToggled(
        val isEnabled: Boolean,
    ) : NotificationCaptureSettingsIntent

    data object CheckNowClicked : NotificationCaptureSettingsIntent

    data class AdKeywordDeleteClicked(
        val keywordId: String,
    ) : NotificationCaptureSettingsIntent

    data object BackClicked : NotificationCaptureSettingsIntent
}

sealed interface NotificationCaptureSettingsEffect {
    data object OpenListenerSettings : NotificationCaptureSettingsEffect

    data object Close : NotificationCaptureSettingsEffect
}
