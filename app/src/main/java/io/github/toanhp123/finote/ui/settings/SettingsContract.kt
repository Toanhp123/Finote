package io.github.toanhp123.finote.ui.settings

/** [bankCount] is the number of supported banks: packages with at least one active rule. */
data class SettingsState(
    val isAccessEnabled: Boolean = false,
    val bankCount: Int = 0,
)

sealed interface SettingsIntent {
    /** The screen came back to the foreground. The screen reads the permission, so this class stays pure. */
    data class Resumed(
        val isGranted: Boolean,
    ) : SettingsIntent

    data object CaptureClicked : SettingsIntent

    data object AccountsClicked : SettingsIntent
}

sealed interface SettingsEffect {
    data object OpenCaptureSettings : SettingsEffect

    data object OpenAccountsSettings : SettingsEffect
}
