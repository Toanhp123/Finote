package io.github.toanhp123.finote.ui.settings

/**
 * [bankCount] is the number of supported banks: packages with at least one active rule. [isToolsEnabled] shows the
 * tool rows (the notification log) and is true in the debug and dogfood builds only.
 */
data class SettingsState(
    val bankCount: Int = 0,
    val isToolsEnabled: Boolean = false,
)

sealed interface SettingsIntent {
    data object CaptureClicked : SettingsIntent

    data object AccountsClicked : SettingsIntent

    data object LogsClicked : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenCaptureSettings : SettingsEffect

    data object OpenAccountsSettings : SettingsEffect

    data object OpenNotificationLogs : SettingsEffect
}
