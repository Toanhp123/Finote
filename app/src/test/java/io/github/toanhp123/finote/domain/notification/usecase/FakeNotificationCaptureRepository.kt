package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository

/** In-memory stand-in: rules are served sorted like the real DAO, logs reject a repeated hash. */
class FakeNotificationCaptureRepository(
    private val rules: List<ParserRule> = emptyList(),
) : NotificationCaptureRepository {
    val logs = mutableListOf<NotificationLog>()

    /** Ids whose outcome write throws, to test that one bad row is skipped. */
    val failingUpdateIds = mutableSetOf<String>()

    /** Every call of [updateFailedOutcome], including the ones that throw. */
    var updateCount = 0
        private set

    override suspend fun getRules(packageName: String): List<ParserRule> =
        rules.filter { it.packageName == packageName }.sortedWith(compareBy({ it.priority }, { it.id }))

    override suspend fun insertLog(log: NotificationLog): Boolean {
        if (logs.any { it.contentHash == log.contentHash }) return false
        logs += log
        return true
    }

    override suspend fun getFailedLogs(packages: Set<String>): List<NotificationLog> =
        logs.filter { it.status == NotificationStatus.FAILED && it.packageName in packages }

    override suspend fun updateFailedOutcome(
        id: String,
        outcome: Classification,
    ): Boolean {
        updateCount++
        check(id !in failingUpdateIds)
        val index = logs.indexOfFirst { it.id == id && it.status == NotificationStatus.FAILED }
        if (index < 0) return false
        logs[index] =
            logs[index].copy(
                title = if (outcome.isSecret) "" else logs[index].title,
                content = if (outcome.isSecret) "" else logs[index].content,
                status = outcome.status,
                failureReason = outcome.failureReason,
                matchedRuleId = outcome.parsed?.ruleId,
                parsed = outcome.parsed?.fields,
            )
        return true
    }
}
