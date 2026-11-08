package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule

interface NotificationCaptureRepository {
    /** Rules of one package, sorted by `priority`, then `id`. */
    suspend fun getRules(packageName: String): List<ParserRule>

    /** Stores the log. False, and nothing stored, when a log with the same `contentHash` exists. */
    suspend fun insertLog(log: NotificationLog): Boolean

    /** The `FAILED` logs of these packages, oldest first. */
    suspend fun getFailedLogs(packages: Set<String>): List<NotificationLog>

    /**
     * Writes a re-parse [outcome] (status, reason, matched rule, parsed fields) to a log that is still `FAILED`.
     * True when the row changed; false when it is gone or its status has moved on.
     */
    suspend fun updateFailedOutcome(
        id: String,
        outcome: Classification,
    ): Boolean
}
