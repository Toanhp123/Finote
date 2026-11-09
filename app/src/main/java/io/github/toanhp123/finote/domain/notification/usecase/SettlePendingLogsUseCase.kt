package io.github.toanhp123.finote.domain.notification.usecase

import io.github.toanhp123.finote.domain.notification.REPOST_WINDOW_WITH_BALANCE_MILLIS
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.planSettlement
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

class SettlePendingLogsUseCase
    @Inject
    constructor(
        private val repository: NotificationSettlementRepository,
        private val clock: Clock,
    ) {
        /**
         * Plans the `PENDING` logs and runs each decision in order. A decision that throws is skipped; its logs
         * stay `PENDING` and the next decisions still run. Returns true when `PENDING` logs remain (the worker
         * retries). Runs one at a time in the whole process.
         */
        suspend operator fun invoke(): Boolean = lock.withLock { settle() }

        private suspend fun settle(): Boolean {
            val pending = repository.getPendingLogs()
            if (pending.isEmpty()) return false
            val now = clock.millis()
            val since = pending.minOf { it.postTime } - REPOST_WINDOW_WITH_BALANCE_MILLIS
            val decisions =
                planSettlement(
                    pending = pending,
                    settled = repository.getSettledLogsSince(since),
                    accounts = repository.getAccounts(),
                    rulesById = repository.getRulesById(),
                    now = now,
                )
            // Transactions booked in this run, so a repost of a log that is still being settled can link to it.
            val booked = HashMap<String, String>()
            // Ids of logs written this run; a repost of a still-PENDING original waits until the original is written.
            val written = HashSet<String>()
            for (decision in decisions) {
                try {
                    execute(decision, booked, written, now)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Skipped: the logs stay PENDING and the next run tries again.
                }
            }
            return repository.getPendingLogs().isNotEmpty()
        }

        private suspend fun execute(
            decision: SettlementDecision,
            booked: MutableMap<String, String>,
            written: MutableSet<String>,
            now: Long,
        ) {
            when (decision) {
                is SettlementDecision.Book -> {
                    val transactionId = UUID.randomUUID().toString()
                    repository.book(decision, transactionId, now)
                    booked[decision.bankSide.id] = transactionId
                    decision.walletSide?.let { booked[it.id] = transactionId }
                    written += listOfNotNull(decision.bankSide.id, decision.walletSide?.id)
                }

                is SettlementDecision.Fail -> {
                    repository.fail(decision)
                    written += listOfNotNull(decision.log.id, decision.partner?.id)
                }

                is SettlementDecision.Repost -> {
                    val original = decision.original
                    if (original.status == NotificationStatus.PENDING && original.id !in written) return
                    repository.markRepost(decision.log.id, original.linkedTransactionId ?: booked[original.id])
                }
            }
        }

        private companion object {
            val lock = Mutex()
        }
    }
