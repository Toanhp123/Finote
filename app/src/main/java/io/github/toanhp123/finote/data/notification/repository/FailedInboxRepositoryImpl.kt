package io.github.toanhp123.finote.data.notification.repository

import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.FinoteDatabase
import io.github.toanhp123.finote.data.database.mapStorageErrors
import io.github.toanhp123.finote.data.database.storage
import io.github.toanhp123.finote.data.database.storageWrite
import io.github.toanhp123.finote.data.notification.local.dao.KeyRow
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.data.transaction.repository.LedgerWriter
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.service.isCoveredByAdjustment
import io.github.toanhp123.finote.domain.common.DefaultDispatcher
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.repository.FailedInboxRepository
import io.github.toanhp123.finote.domain.notification.service.FAIL_INVALID_TIME
import io.github.toanhp123.finote.domain.notification.service.FAIL_NO_RULE_MATCHED
import io.github.toanhp123.finote.domain.notification.service.INBOX_REASONS
import io.github.toanhp123.finote.domain.notification.service.REPOST_WINDOW_WITH_BALANCE_MILLIS
import io.github.toanhp123.finote.domain.notification.service.buildFailedInbox
import io.github.toanhp123.finote.domain.notification.service.extractVndAmount
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlin.math.abs

// Stays under SQLite's 999 variable limit, which older system SQLite enforces.
private const val KEY_CHUNK = 500

class FailedInboxRepositoryImpl
    @Inject
    constructor(
        private val db: FinoteDatabase,
        clock: Clock,
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : FailedInboxRepository {
        private val logs = db.notificationLogDao()
        private val rules = db.parserRuleDao()
        private val identifiers = db.accountIdentifierDao()
        private val ledger = LedgerWriter(db, clock)

        override fun observeInbox(): Flow<List<FailedInboxItem>> {
            val activePackages =
                rules
                    .observeAll()
                    .map { list -> list.filter { it.isActive }.map { it.packageName }.toSet() }
                    .distinctUntilChanged()
            return combine(logs.observeInboxLogs(), activePackages) { entities, active ->
                val candidates = entities.map { it.toDomain() }
                buildFailedInbox(candidates.withoutBookedVersions(), active)
            }.distinctUntilChanged().flowOn(defaultDispatcher).mapStorageErrors()
        }

        override suspend fun getItem(id: String): FailedInboxItem? = observeInbox().first().firstOrNull { it.id == id }

        override suspend fun getIdentifiers(packageName: String): List<AccountIdentifier> =
            storage { identifiers.getByPackage(packageName).map { it.toDomain() } }

        override suspend fun book(
            booking: InboxBooking,
            confirmation: AdjustmentToken?,
            transactionId: String,
            now: Long,
        ): BookInboxResult =
            try {
                db.storageWrite { bookInWrite(booking, confirmation, transactionId, now) }
            } catch (_: GuardMiss) {
                BookInboxResult.AlreadyHandled
            }

        private suspend fun bookInWrite(
            booking: InboxBooking,
            confirmation: AdjustmentToken?,
            transactionId: String,
            now: Long,
        ): BookInboxResult {
            val item = loadItem(booking.itemId, booking.logIds) ?: return BookInboxResult.AlreadyHandled
            val fields =
                TransactionFields(booking.amount, 0L, booking.type, booking.accountId, null, booking.categoryId)
            return TransactionRules.checkFields(fields)?.let { BookInboxResult.Refused(it) }
                ?: adjustmentGate(booking, confirmation, item.log)
                ?: insertBooking(booking, item, transactionId, now)
        }

        /**
         * Null lets the booking go on. Proven covered: the picked account is the one the prefill found and an
         * adjustment after the log time includes it. Possibly covered: a live adjustment of the account after `since`,
         * which the user must confirm with a token that still matches.
         */
        private suspend fun adjustmentGate(
            booking: InboxBooking,
            confirmation: AdjustmentToken?,
            log: NotificationLog,
        ): BookInboxResult? {
            val rows = db.transactionDao().getActiveByAccount(booking.accountId).map { it.toDomain() }
            val isTimeValid = log.failureReason != FAIL_INVALID_TIME
            val isCovered =
                isTimeValid &&
                    booking.accountId == booking.resolvedAccountId &&
                    isCoveredByAdjustment(booking.accountId, log.postTime, rows)
            if (isCovered) return BookInboxResult.CoveredByAdjustment
            val since = if (isTimeValid) minOf(log.postTime, booking.timestamp) else booking.timestamp
            return rows
                .filter { it.type == TransactionType.ADJUSTMENT && it.timestamp > since }
                .maxOfOrNull { it.timestamp }
                ?.takeUnless { confirmation.confirms(booking.accountId, since, it) }
                ?.let { BookInboxResult.NeedsAdjustmentConfirmation(AdjustmentToken(booking.accountId, since, it)) }
        }

        private fun AdjustmentToken?.confirms(
            accountId: String,
            since: Long,
            newestAdjustmentAt: Long,
        ) = this != null &&
            this.accountId == accountId &&
            this.since == since &&
            newestAdjustmentAt <= this.newestAdjustmentAt

        private suspend fun insertBooking(
            booking: InboxBooking,
            item: InboxLogs,
            transactionId: String,
            now: Long,
        ): BookInboxResult {
            val transaction =
                Transaction(
                    id = transactionId,
                    amount = booking.amount,
                    feeAmount = 0L,
                    type = booking.type,
                    adjustmentDirection = null,
                    accountId = booking.accountId,
                    toAccountId = null,
                    timestamp = booking.timestamp,
                    deletedAt = null,
                    createdAt = now,
                    updatedAt = now,
                    categoryId = booking.categoryId,
                    note = booking.note,
                    source = TransactionSource.MANUAL,
                    postBalance = null,
                    notificationLogId = item.log.id,
                )
            val isAccountUsable =
                db
                    .accountDao()
                    .getById(booking.accountId)
                    ?.toDomain()
                    ?.isUsable == true
            val categoryType = booking.categoryId?.let { db.categoryDao().getById(it)?.type }
            val refusal =
                TransactionRules.check(
                    transaction,
                    Instant.ofEpochMilli(now),
                    if (isAccountUsable) setOf(booking.accountId) else emptySet(),
                    categoryType,
                )
            if (refusal != null) return BookInboxResult.Refused(refusal)
            ledger.insert(transaction)
            // The logs were read in this write, so a miss means something is wrong: undo the insert too.
            val confirmed = logs.confirmFromInbox(item.ids, transactionId, booking.amount, booking.type.name)
            if (confirmed != item.ids.size) throw GuardMiss()
            return BookInboxResult.Booked
        }

        override suspend fun dismiss(
            itemId: String,
            logIds: List<String>,
        ): Boolean =
            try {
                db.storageWrite {
                    val ids = loadItem(itemId, logIds)?.ids ?: return@storageWrite false
                    if (logs.dismissFromInbox(ids) != ids.size) throw GuardMiss()
                    true
                }
            } catch (_: GuardMiss) {
                false
            }

        /**
         * The item's representative log and the ids of every log it stands for, read now. Null when the log is no
         * longer a failed inbox log, or when any of the [expected] ids (the copies the user saw) is gone from the
         * group: settled, re-parsed, or covered by a booked later version of the same key. Identical copies are one
         * event, so booking the rest would count it twice. A copy that arrived since is in the result and is absorbed.
         */
        private suspend fun loadItem(
            itemId: String,
            expected: List<String>,
        ): InboxLogs? {
            val log =
                logs.getById(itemId)?.toDomain()?.takeIf {
                    it.status == NotificationStatus.FAILED && it.failureReason in INBOX_REASONS
                } ?: return null
            val copies =
                if (log.failureReason == FAIL_NO_RULE_MATCHED) {
                    logs
                        .getUnparsedCopies(log.packageName, log.title, log.content)
                        .map { it.toDomain() }
                        .withoutBookedVersions()
                } else {
                    listOf(log)
                }
            val ids = copies.map { it.id }
            return if (log.id in ids && ids.containsAll(expected)) InboxLogs(log, ids) else null
        }

        /**
         * Drops the `no_rule_matched` logs that a booked log already covers: same package, same non-blank key, within
         * the repost window of the log's own time, and the same amount (the log's readable amount equals the booked
         * `parsedAmount`). Done per log, not per key, and the amount must agree: an app may reuse one key for every
         * posting, so a log with another or no readable amount stays in the inbox.
         */
        private suspend fun List<NotificationLog>.withoutBookedVersions(): List<NotificationLog> {
            val keys =
                filter { it.failureReason == FAIL_NO_RULE_MATCHED }
                    .mapNotNull { it.notificationKey?.takeIf(String::isNotBlank) }
                    .distinct()
            if (keys.isEmpty()) return this
            val handled = storage { keys.chunked(KEY_CHUNK).flatMap { logs.getHandledKeys(it) } }
            return filterNot { log -> log.failureReason == FAIL_NO_RULE_MATCHED && handled.any { it.covers(log) } }
        }

        private fun KeyRow.covers(log: NotificationLog) =
            packageName == log.packageName &&
                notificationKey == log.notificationKey &&
                abs(postTime - log.postTime) <= REPOST_WINDOW_WITH_BALANCE_MILLIS &&
                parsedAmount != null &&
                parsedAmount == extractVndAmount(log.title, log.content)

        private class InboxLogs(
            val log: NotificationLog,
            val ids: List<String>,
        )

        // Thrown inside the write to undo it and caught outside; like the runner's rollback signal, without a trace.
        private class GuardMiss : Exception(null, null, false, false)
    }
