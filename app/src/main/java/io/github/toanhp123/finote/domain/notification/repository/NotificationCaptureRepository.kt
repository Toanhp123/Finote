package io.github.toanhp123.finote.domain.notification.repository

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule

interface NotificationCaptureRepository {
    /** Rules of one package, sorted by `priority`, then `id`. */
    suspend fun getRules(packageName: String): List<ParserRule>

    /** Stores the log. False, and nothing stored, when a log with the same `contentHash` exists. */
    suspend fun insertLog(log: NotificationLog): Boolean
}
