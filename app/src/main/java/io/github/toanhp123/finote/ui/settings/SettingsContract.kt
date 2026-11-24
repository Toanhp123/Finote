package io.github.toanhp123.finote.ui.settings

/**
 * [bankCount] is the number of supported banks: packages with at least one active rule. [isToolsEnabled] shows the
 * tool rows (the notification log) and is true in the debug and dogfood builds only. [inboxCount] is the failed
 * inbox items to review and [hasUnseenInbox] puts a red dot on its row.
 */
data class SettingsState(
    val bankCount: Int = 0,
    val isToolsEnabled: Boolean = false,
    val inboxCount: Int = 0,
    val hasUnseenInbox: Boolean = false,
)

sealed interface SettingsIntent {
    data object CaptureClicked : SettingsIntent

    data object AccountsClicked : SettingsIntent

    data object InboxClicked : SettingsIntent

    data object LogsClicked : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenCaptureSettings : SettingsEffect

    data object OpenAccountsSettings : SettingsEffect

    data object OpenFailedInbox : SettingsEffect

    data object OpenNotificationLogs : SettingsEffect
}
