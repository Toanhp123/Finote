package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.model.Classification
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Copies from the real repository: rule order, a repeated `contentHash` is rejected, `getReparseLogs` filter and order.
 * Does not copy: business `require` checks (R4/R5 of a later plan move them into the domain).
 */
class FakeNotificationCaptureRepository(
    private val rules: List<ParserRule> = emptyList(),
) : NotificationCaptureRepository {
    val logs = mutableListOf<NotificationLog>()

    /** Normalized user keywords per package; tests fill it. */
    val userKeywords = mutableMapOf<String, List<String>>()

    /** Ids whose outcome write throws, to test that one bad row is skipped. */
    val failingUpdateIds = mutableSetOf<String>()

    /** Every call of [updateFailedOutcome], including the ones that throw. */
    var updateCount = 0
        private set

    /** Backs [observeRecentLogs]; tests set it directly, it is not tied to [logs]. */
    val recentLogs = MutableStateFlow<List<NotificationLog>>(emptyList())

    override fun observeRecentLogs(limit: Int): Flow<List<NotificationLog>> = recentLogs

    override suspend fun getRules(packageName: String): List<ParserRule> =
        rules.filter { it.packageName == packageName }.sortedWith(compareBy({ it.priority }, { it.id }))

    override suspend fun insertLog(log: NotificationLog): Boolean {
        if (logs.any { it.contentHash == log.contentHash }) return false
        logs += log
        return true
    }

    override suspend fun getUserKeywords(packageName: String): List<String> = userKeywords[packageName].orEmpty()

    override suspend fun getReparseLogs(packages: Set<String>): List<NotificationLog> =
        logs
            .filter { it.isReparseCandidate() && it.packageName in packages }
            .sortedWith(compareBy({ it.receivedAt }, { it.id }))

    override suspend fun updateFailedOutcome(
        id: String,
        outcome: Classification,
    ): Boolean {
        updateCount++
        check(id !in failingUpdateIds)
        val index = logs.indexOfFirst { it.id == id && it.isReparseCandidate() }
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

    private fun NotificationLog.isReparseCandidate() =
        (status == NotificationStatus.FAILED && failureReason == "no_rule_matched") ||
            (status == NotificationStatus.IGNORED_SPAM && failureReason.orEmpty().startsWith("user_keyword:"))
}
