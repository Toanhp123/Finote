package io.github.toanhp123.finote.ui.settings.capture

import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.ui.settings.logic.BankSummary

/**
 * [isChecking] is true while an update runs. [lastCheck] is null before the first one.
 * [banks] lists the supported banks, read only.
 */
data class NotificationCaptureSettingsState(
    val isAutoUpdateEnabled: Boolean = false,
    val isChecking: Boolean = false,
    val lastCheck: RuleUpdateCheck? = null,
    val banks: List<BankSummary> = emptyList(),
)

sealed interface NotificationCaptureSettingsIntent {
    data object AccessClicked : NotificationCaptureSettingsIntent

    data class AutoUpdateToggled(
        val isEnabled: Boolean,
    ) : NotificationCaptureSettingsIntent

    data object CheckNowClicked : NotificationCaptureSettingsIntent

    data object BackClicked : NotificationCaptureSettingsIntent
}

sealed interface NotificationCaptureSettingsEffect {
    data object OpenListenerSettings : NotificationCaptureSettingsEffect

    data object Close : NotificationCaptureSettingsEffect
}
