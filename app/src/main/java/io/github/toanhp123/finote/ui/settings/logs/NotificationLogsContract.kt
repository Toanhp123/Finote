package io.github.toanhp123.finote.ui.settings.logs

import io.github.toanhp123.finote.domain.notification.model.NotificationLog

/**
 * [logs] are the newest captures, newest first. [bankNames] maps a package to its bank name.
 * [selected] is the log whose sheet is open.
 * The state holds [NotificationLog] itself, so its `toString()` stays free of notification text.
 */
data class NotificationLogsState(
    val logs: List<NotificationLog> = emptyList(),
    val bankNames: Map<String, String> = emptyMap(),
    val selected: NotificationLog? = null,
) {
    /** A secret log has no text, so there is nothing to copy. */
    val isCopyEnabled: Boolean get() =
        selected != null &&
            (selected.title.isNotBlank() || selected.content.isNotBlank())
}

sealed interface NotificationLogsIntent {
    data class LogClicked(
        val id: String,
    ) : NotificationLogsIntent

    data object SheetDismissed : NotificationLogsIntent

    data object CopyClicked : NotificationLogsIntent

    data object BackClicked : NotificationLogsIntent
}

sealed interface NotificationLogsEffect {
    /** [json] is the masked sample. `toString()` hides it, so no log or crash report carries it. */
    data class CopySample(
        val json: String,
    ) : NotificationLogsEffect {
        override fun toString() = "CopySample"
    }

    data object Close : NotificationLogsEffect
}
