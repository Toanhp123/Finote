package io.github.toanhp123.finote.domain.notification.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.data.notification.repository.NotificationSettlementRepositoryImpl
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.repository.NotificationSettlementRepository
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** [SettlePendingLogsUseCase] on the real Room runner and repository: a stale `book` rolls back as one unit. */
@RunWith(AndroidJUnit4::class)
class SettlementTransactionTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
    private val repo by lazy { NotificationSettlementRepositoryImpl(dbRule.db, clock) }

    /** Plans against the real data, then another run settles the log before `book` runs. */
    private inner class SettlesAfterPlanning : NotificationSettlementRepository by repo {
        override suspend fun getAccounts() =
            repo.getAccounts().also {
                dbRule.db
                    .notificationLogDao()
                    .markSettled(listOf("b1"), NotificationStatus.CONFIRMED, "tx-other", null)
            }
    }

    @Test
    fun `book of a log settled after planning rolls back the new account and transaction`() =
        runTest {
            dbRule.db.parserRuleDao().insert(parserRuleEntity(id = "rule", packageName = PKG))
            val postTime = clock.millis() - 60_000L
            val log =
                NotificationLog(
                    id = "b1",
                    packageName = PKG,
                    channelId = null,
                    title = "t",
                    content = "c",
                    contentHash = "h-b1",
                    postTime = postTime,
                    receivedAt = postTime,
                    status = NotificationStatus.PENDING,
                    failureReason = null,
                    matchedRuleId = "rule",
                    parsed = ParsedFields(50_000L, TransactionType.EXPENSE, "1234", 950_000L, null, null),
                )
            dbRule.db.notificationLogDao().insert(log.toEntity())

            val pendingLeft =
                SettlePendingLogsUseCase(SettlesAfterPlanning(), RoomTransactionRunner(dbRule.db), clock)()

            assertFalse(pendingLeft)
            assertTrue(
                dbRule.db
                    .accountDao()
                    .getWithBankKey()
                    .isEmpty(),
            )
            assertTrue(
                dbRule.db
                    .transactionDao()
                    .observeTransactions(null)
                    .first()
                    .isEmpty(),
            )
            val stored =
                dbRule.db
                    .notificationLogDao()
                    .getAll()
                    .single()
            assertEquals(NotificationStatus.CONFIRMED, stored.status)
            assertEquals("tx-other", stored.linkedTransactionId)
        }

    private companion object {
        const val PKG = "com.test.bank"
    }
}
