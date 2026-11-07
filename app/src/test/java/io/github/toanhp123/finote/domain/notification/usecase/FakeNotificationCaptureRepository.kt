package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository

/** In-memory stand-in: rules are served sorted like the real DAO, logs reject a repeated hash. */
class FakeNotificationCaptureRepository(
    private val rules: List<ParserRule> = emptyList(),
) : NotificationCaptureRepository {
    val logs = mutableListOf<NotificationLog>()

    override suspend fun getRules(packageName: String): List<ParserRule> =
        rules.filter { it.packageName == packageName }.sortedWith(compareBy({ it.priority }, { it.id }))

    override suspend fun insertLog(log: NotificationLog): Boolean {
        if (logs.any { it.contentHash == log.contentHash }) return false
        logs += log
        return true
    }
}
