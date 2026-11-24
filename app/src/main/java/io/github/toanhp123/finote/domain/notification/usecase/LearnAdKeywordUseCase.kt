package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.TransactionRunner
import io.github.toanhp123.finote.domain.notification.model.AdKeywordRefusal
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.repository.UserKeywordRepository
import io.github.toanhp123.finote.domain.notification.service.adKeywordMatches
import io.github.toanhp123.finote.domain.notification.service.checkAdKeyword
import io.github.toanhp123.finote.domain.notification.service.normalizeKeyword
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class LearnAdKeywordUseCase
    @Inject
    constructor(
        private val repository: UserKeywordRepository,
        private val tx: TransactionRunner,
        private val clock: Clock,
    ) {
        /**
         * Teaches [keyword] as an ad from the log [selectedLogId]. Ok holds the new keyword id. A refusal writes
         * nothing. The logs to hide are chosen before the write, so no regex runs while it holds the lock; the write
         * only touches the ones that are still unparsed.
         */
        suspend operator fun invoke(
            selectedLogId: String,
            keyword: String,
        ): Outcome<String, AdKeywordRefusal> {
            val selected = repository.getLog(selectedLogId) ?: return Outcome.Err(AdKeywordRefusal.NOT_IN_SELECTED)
            val packageName = selected.packageName
            val logs = repository.getLogs(packageName)
            val existing = repository.getKeywords(packageName).map { it.normalizedKeyword }
            val refusal = checkAdKeyword(keyword, selected, logs, repository.getBankName(packageName), existing)
            return if (refusal != null) Outcome.Err(refusal) else teach(packageName, keyword, logs)
        }

        private suspend fun teach(
            packageName: String,
            keyword: String,
            logs: List<NotificationLog>,
        ): Outcome<String, AdKeywordRefusal> {
            val taught =
                UserIgnoreKeyword(
                    id = UUID.randomUUID().toString(),
                    packageName = packageName,
                    keyword = keyword.trim(),
                    normalizedKeyword = normalizeKeyword(keyword),
                    createdAt = clock.millis(),
                )
            val logIds = adKeywordMatches(keyword, logs).map { it.id }
            return tx.write {
                // A keyword taught since the read in invoke: refuse instead of tripping the unique index.
                val isTaughtMeanwhile =
                    repository.getKeywords(packageName).any { it.normalizedKeyword == taught.normalizedKeyword }
                if (isTaughtMeanwhile) {
                    Outcome.Err(AdKeywordRefusal.DUPLICATE)
                } else {
                    repository.add(taught, logIds)
                    Outcome.Ok(taught.id)
                }
            }
        }
    }
