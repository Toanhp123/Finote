package io.github.toanhp123.finote.ui.setup

enum class SetupStep { INTRO, RETRY, GRANTED }

/** [hasLaunchedSettings] is true once the user opened the system settings, so a resume without access means retry. */
data class SetupState(
    val step: SetupStep = SetupStep.INTRO,
    val hasLaunchedSettings: Boolean = false,
)

sealed interface SetupIntent {
    /** The screen came back to the foreground. The screen reads the permission, so this class stays pure. */
    data class Resumed(
        val isGranted: Boolean,
    ) : SetupIntent

    data object EnableClicked : SetupIntent

    data object LaterClicked : SetupIntent
}

sealed interface SetupEffect {
    data object OpenListenerSettings : SetupEffect

    /** Setup is over. The nav host decides what to show next. */
    data object Finished : SetupEffect
}
