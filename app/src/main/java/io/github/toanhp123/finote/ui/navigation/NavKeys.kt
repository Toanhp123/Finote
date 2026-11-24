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

/** The accounts page (merge, ignore), pushed on top of [Settings]. */
@Serializable
data object AccountsSettings : NavKey

/** The notification capture page, pushed on top of [Settings]. */
@Serializable
data object NotificationCaptureSettings : NavKey

/** The notification log page, pushed on top of [Settings]. Registered only when tools are enabled. */
@Serializable
data object NotificationLogs : NavKey

/** The failed notifications inbox, pushed on top of [Settings]. */
@Serializable
data object FailedInbox : NavKey

/** The booking sheet of the inbox item [itemId], drawn over the inbox. */
@Serializable
data class BookInboxItem(
    val itemId: String,
) : NavKey

/** The cash entry sheet in edit mode for the transaction [id], drawn over the list below it. */
@Serializable
data class EditTransaction(
    val id: String,
) : NavKey
