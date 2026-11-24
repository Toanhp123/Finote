package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.DefaultDispatcher
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.RawNotification
import io.github.toanhp123.finote.domain.notification.port.SettleScheduler
import io.github.toanhp123.finote.domain.notification.repository.NotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.service.classifyNotification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ReparseFailedLogsUseCase
    @Inject
    constructor(
        private val repository: NotificationCaptureRepository,
        @param:DefaultDispatcher private val parseDispatcher: CoroutineDispatcher,
        private val settleScheduler: SettleScheduler,
    ) {
        /**
         * Runs the current rules and user keywords again over the `FAILED` logs of [packages] and the logs a user
         * keyword hid. A log that now matches becomes `PENDING`; one that a filter blocks becomes `IGNORED_SPAM`; a
         * keyword-hidden log whose keyword is gone and no rule reads becomes `FAILED` again. A log whose new outcome
         * equals its stored status and reason is left alone. A log that throws is skipped and keeps its status.
         * Schedules one settle when a log became `PENDING`. Returns the number of logs changed.
         */
        suspend operator fun invoke(packages: Set<String>): Int {
            val rulesByPackage = packages.associateWith { repository.getRules(it) }
            val keywordsByPackage =
                rulesByPackage.mapValues { (packageName, rules) ->
                    if (rules.isEmpty()) emptyList() else repository.getUserKeywords(packageName)
                }
            var changed = 0
            var hasPending = false
            for (log in repository.getReparseLogs(packages)) {
                // A package without rules would read as "channel not allowed", so its logs are left as they are.
                val rules = rulesByPackage[log.packageName].orEmpty()
                val status =
                    if (rules.isEmpty()) {
                        null
                    } else {
                        reparse(
                            log,
                            rules,
                            keywordsByPackage[log.packageName].orEmpty(),
                        )
                    }
                if (status != null) changed++
                if (status == NotificationStatus.PENDING) hasPending = true
            }
            if (hasPending) settleScheduler.scheduleAfterCapture()
            return changed
        }

        private suspend fun reparse(
            log: NotificationLog,
            rules: List<ParserRule>,
            userKeywords: List<String>,
        ): NotificationStatus? =
            try {
                val raw =
                    RawNotification(
                        log.packageName,
                        log.channelId,
                        log.title,
                        log.content,
                        log.postTime,
                        log.notificationKey,
                    )
                val outcome = withContext(parseDispatcher) { classifyNotification(raw, rules, userKeywords) }
                val isSame = outcome.status == log.status && outcome.failureReason == log.failureReason
                val isChanged = !isSame && repository.updateFailedOutcome(log.id, outcome)
                if (isChanged) outcome.status else null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
    }
