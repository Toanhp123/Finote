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
         * Runs the current rules again over the `FAILED` logs of [packages]. A log that now matches becomes
         * `PENDING`; one that a new filter blocks becomes `IGNORED_SPAM`; one that still has no match is left alone.
         * A log that throws is skipped and stays `FAILED`. Schedules one settle when a log became `PENDING`.
         * Returns the number of logs changed.
         */
        suspend operator fun invoke(packages: Set<String>): Int {
            val rulesByPackage = packages.associateWith { repository.getRules(it) }
            var changed = 0
            var hasPending = false
            for (log in repository.getFailedLogs(packages)) {
                // A package without rules would read as "channel not allowed", so its logs are left as they are.
                val rules = rulesByPackage[log.packageName].orEmpty()
                val status = if (rules.isEmpty()) null else reparse(log, rules)
                if (status != null) changed++
                if (status == NotificationStatus.PENDING) hasPending = true
            }
            if (hasPending) settleScheduler.scheduleAfterCapture()
            return changed
        }

        private suspend fun reparse(
            log: NotificationLog,
            rules: List<ParserRule>,
        ): NotificationStatus? =
            try {
                val raw = RawNotification(log.packageName, log.channelId, log.title, log.content, log.postTime)
                val outcome = withContext(parseDispatcher) { classifyNotification(raw, rules) }
                val isChanged =
                    outcome.status != NotificationStatus.FAILED && repository.updateFailedOutcome(log.id, outcome)
                if (isChanged) outcome.status else null
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
    }
