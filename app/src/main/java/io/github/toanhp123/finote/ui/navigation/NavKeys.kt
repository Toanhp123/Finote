package io.github.toanhp123.finote.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Overview : NavKey

@Serializable
data object Transactions : NavKey

@Serializable
data object Budget : NavKey

@Serializable
data object Settings : NavKey

/**
 * The notification access setup screen. [isFromOnboarding] is true on the first run, when it is the root of the stack,
 * and false when the listener-off banner pushed it on top of another screen.
 */
@Serializable
data class Setup(
    val isFromOnboarding: Boolean,
) : NavKey
