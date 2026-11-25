package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.data.notification.local.entity.NotificationLogEntity
import io.github.toanhp123.finote.data.notification.notificationLogEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.service.FAIL_INVALID_TIME
import io.github.toanhp123.finote.domain.notification.service.FAIL_NO_RULE_MATCHED
import io.github.toanhp123.finote.domain.notification.service.FAIL_SETTLE_ERROR
import io.github.toanhp123.finote.domain.notification.service.FAIL_USER_DISMISSED
import io.github.toanhp123.finote.domain.notification.service.INBOX_REASONS
import io.github.toanhp123.finote.domain.notification.usecase.BookInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DismissInboxItemUseCase
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private const val PKG = "com.test.bank"
private const val ACC = "acc-1"
private const val OTHER = "acc-2"
private const val THIRD = "acc-3"
private const val AMOUNT = 150_000L
private const val HOUR = 3_600_000L

// 08:00 in UTC+7, three days before the clock, so it differs from a noon-of-that-day default.
private val POST = Instant.parse("2026-11-20T01:00:00Z").toEpochMilli()

private fun failedLog(
    id: String,
    reason: String = FAIL_SETTLE_ERROR,
    postTime: Long = POST,
    content: String = "content",
) = notificationLogEntity(id = id, contentHash = "hash-$id", status = NotificationStatus.FAILED).copy(
    failureReason = reason,
    postTime = postTime,
    content = content,
)

private fun adjustment(
    id: String,
    accountId: String,
    timestamp: Long,
): TransactionEntity =
    transaction(id = id, accountId = accountId, type = TransactionType.ADJUSTMENT, timestamp = timestamp).copy(
        adjustmentDirection = AdjustmentDirection.IN,
        source = TransactionSource.ADJUSTMENT,
    )

private const val SPEND = "Thanh toan 150,000d tai Cafe"

private fun bookedLog(
    key: String,
    postTime: Long,
    amount: Long,
) = notificationLogEntity(id = "booked", contentHash = "hash-booked", status = NotificationStatus.CONFIRMED)
    .copy(postTime = postTime, notificationKey = key, parsedAmount = amount)

@RunWith(AndroidJUnit4::class)
class FailedInboxBookingTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-23T05:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: FailedInboxRepositoryImpl
    private lateinit var book: BookInboxItemUseCase
    private lateinit var dismiss: DismissInboxItemUseCase

    private val logDao get() = dbRule.db.notificationLogDao()
    private val txDao get() = dbRule.db.transactionDao()

    // why: Room emits from its own executor, which a test scheduler rejects; Unconfined has no scheduler.
    @Suppress("InjectDispatcher")
    @Before
    fun setUp() {
        repo = FailedInboxRepositoryImpl(dbRule.db, clock, Dispatchers.Unconfined)
        book = BookInboxItemUseCase(repo, RoomTransactionRunner(dbRule.db), clock)
        dismiss = DismissInboxItemUseCase(repo)
    }

    private suspend fun seed(
        vararg logs: NotificationLogEntity,
        accounts: List<String> = listOf(ACC),
    ) {
        dbRule.db.parserRuleDao().insert(parserRuleEntity(packageName = PKG))
        accounts.forEach { dbRule.db.accountDao().insert(account(id = it, bankPackageName = PKG)) }
        logs.forEach { logDao.insert(it) }
    }

    private fun booking(
        itemId: String = "log-1",
        logIds: List<String> = listOf(itemId),
        accountId: String = ACC,
        timestamp: Long = POST,
        resolvedAccountId: String? = null,
        categoryId: String? = null,
    ) = InboxBooking(
        itemId,
        logIds,
        AMOUNT,
        TransactionType.EXPENSE,
        accountId,
        categoryId,
        "Cafe",
        timestamp,
        resolvedAccountId,
    )

    private suspend fun balanceOf(accountId: String) =
        dbRule.db
            .accountDao()
            .getById(accountId)
            ?.currentBalance

    private suspend fun statusOf(id: String) = logDao.getById(id)?.status

    @Test
    fun `booking writes one manual row and confirms the log`() =
        runTest {
            seed(failedLog("log-1"))

            assertEquals(BookInboxResult.Booked, book(booking()))

            val rows = txDao.getActiveByAccount(ACC)
            assertEquals(1, rows.size)
            val log = logDao.getById("log-1")
            assertEquals(NotificationStatus.CONFIRMED, log?.status)
            assertEquals(rows.single().id, log?.linkedTransactionId)
            assertEquals("log-1", rows.single().notificationLogId)
            assertEquals(1_000_000L - AMOUNT, balanceOf(ACC))
        }

    @Test
    fun `booked row keeps the exact post time`() =
        runTest {
            seed(failedLog("log-1", postTime = POST))

            book(booking(timestamp = POST))

            assertEquals(POST, txDao.getActiveByAccount(ACC).single().timestamp)
        }

    @Test
    fun `booked row has null post balance and manual source`() =
        runTest {
            seed(failedLog("log-1"))

            book(booking())

            val row = txDao.getActiveByAccount(ACC).single()
            assertNull(row.postBalance)
            assertEquals(TransactionSource.MANUAL, row.source)
        }

    @Test
    fun `booking a copy group confirms every copy`() =
        runTest {
            val older = failedLog("copy-a", FAIL_NO_RULE_MATCHED, postTime = POST - HOUR)
            val newer = failedLog("copy-b", FAIL_NO_RULE_MATCHED, postTime = POST)
            seed(older, newer)

            assertEquals(BookInboxResult.Booked, book(booking(itemId = "copy-a", logIds = listOf("copy-a", "copy-b"))))

            val tx = txDao.getActiveByAccount(ACC).single()
            listOf("copy-a", "copy-b").forEach {
                assertEquals(NotificationStatus.CONFIRMED, statusOf(it))
                assertEquals(tx.id, logDao.getById(it)?.linkedTransactionId)
            }
            assertEquals("copy-a", tx.notificationLogId)
        }

    @Test
    fun `log settled meanwhile returns AlreadyHandled and writes nothing`() =
        runTest {
            seed(failedLog("log-1"))
            dbRule.execSql("UPDATE notification_logs SET status = 'CONFIRMED' WHERE id = 'log-1'")

            assertEquals(BookInboxResult.AlreadyHandled, book(booking()))

            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(1_000_000L, balanceOf(ACC))
        }

    @Test
    fun `a miss of the confirm guard rolls the booking back`() =
        runTest {
            seed(failedLog("copy-a", FAIL_NO_RULE_MATCHED, POST - HOUR), failedLog("copy-b", FAIL_NO_RULE_MATCHED))
            // RAISE(IGNORE) skips the update of one copy, so the guard changes 1 row of 2.
            dbRule.execSql(
                "CREATE TRIGGER skip_a BEFORE UPDATE ON notification_logs WHEN OLD.id = 'copy-a' " +
                    "BEGIN SELECT RAISE(IGNORE); END",
            )

            assertEquals(
                BookInboxResult.AlreadyHandled,
                book(booking(itemId = "copy-a", logIds = listOf("copy-a", "copy-b"))),
            )

            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(1_000_000L, balanceOf(ACC))
            assertEquals(NotificationStatus.FAILED, statusOf("copy-a"))
        }

    @Test
    fun `a copy that left FAILED after the sheet opened refuses the booking`() =
        runTest {
            seed(failedLog("copy-a", FAIL_NO_RULE_MATCHED, POST - HOUR), failedLog("copy-b", FAIL_NO_RULE_MATCHED))
            dbRule.execSql("UPDATE notification_logs SET status = 'PENDING' WHERE id = 'copy-b'")

            val result = book(booking(itemId = "copy-a", logIds = listOf("copy-a", "copy-b")))

            assertEquals(BookInboxResult.AlreadyHandled, result)
            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(NotificationStatus.FAILED, statusOf("copy-a"))
            assertFalse(dismiss("copy-a", listOf("copy-a", "copy-b")))
            assertEquals(FAIL_NO_RULE_MATCHED, logDao.getById("copy-a")?.failureReason)
        }

    @Test
    fun `a booked version covering one copy refuses the booking`() =
        runTest {
            val key = "key-1"
            seed(
                failedLog("copy-a", FAIL_NO_RULE_MATCHED, POST - 2 * HOUR, SPEND),
                failedLog("copy-b", FAIL_NO_RULE_MATCHED, POST, SPEND).copy(notificationKey = key),
                bookedLog(key, POST + 1_000L, AMOUNT),
            )

            val result = book(booking(itemId = "copy-a", logIds = listOf("copy-a", "copy-b")))

            assertEquals(BookInboxResult.AlreadyHandled, result)
            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(NotificationStatus.FAILED, statusOf("copy-a"))
        }

    @Test
    fun `a booked log of the same key and amount covers the log and booking it is AlreadyHandled`() =
        runTest {
            seed(
                failedLog("log-1", FAIL_NO_RULE_MATCHED, content = SPEND).copy(notificationKey = "key-1"),
                bookedLog("key-1", POST + 1_000L, AMOUNT),
            )

            assertNull(repo.getItem("log-1"))
            assertEquals(BookInboxResult.AlreadyHandled, book(booking()))
            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
        }

    @Test
    fun `a booked log of the same key and another amount leaves the log bookable`() =
        runTest {
            seed(
                failedLog("log-1", FAIL_NO_RULE_MATCHED, content = SPEND).copy(notificationKey = "key-1"),
                bookedLog("key-1", POST + 1_000L, AMOUNT + 1),
            )

            assertNotNull(repo.getItem("log-1"))
            assertEquals(BookInboxResult.Booked, book(booking()))
            assertEquals(NotificationStatus.CONFIRMED, statusOf("log-1"))
        }

    @Test
    fun `booking an unparsed log stores the booked amount and type on it`() =
        runTest {
            seed(failedLog("log-1", FAIL_NO_RULE_MATCHED, content = SPEND))

            book(booking())

            val log = logDao.getById("log-1")
            assertEquals(AMOUNT, log?.parsedAmount)
            assertEquals(TransactionType.EXPENSE.name, log?.parsedType)
        }

    @Test
    fun `booking a parsed log keeps its parsed amount and type`() =
        runTest {
            seed(failedLog("log-1").copy(parsedAmount = AMOUNT + 1, parsedType = TransactionType.INCOME.name))

            book(booking())

            val log = logDao.getById("log-1")
            assertEquals(AMOUNT + 1, log?.parsedAmount)
            assertEquals(TransactionType.INCOME.name, log?.parsedType)
        }

    @Test
    fun `a repost of a log booked from the inbox does not come back`() =
        runTest {
            seed(failedLog("log-1", FAIL_NO_RULE_MATCHED, content = SPEND).copy(notificationKey = "key-1"))
            book(booking())

            logDao.insert(
                failedLog("log-2", FAIL_NO_RULE_MATCHED, POST + 1_000L, "$SPEND.").copy(notificationKey = "key-1"),
            )

            assertNull(repo.getItem("log-2"))
        }

    @Test
    fun `a copy that arrived after the sheet opened is confirmed too`() =
        runTest {
            seed(failedLog("copy-a", FAIL_NO_RULE_MATCHED, POST - HOUR), failedLog("copy-b", FAIL_NO_RULE_MATCHED))
            logDao.insert(failedLog("copy-c", FAIL_NO_RULE_MATCHED, POST + HOUR))

            assertEquals(BookInboxResult.Booked, book(booking(itemId = "copy-a", logIds = listOf("copy-a", "copy-b"))))

            listOf("copy-a", "copy-b", "copy-c").forEach { assertEquals(NotificationStatus.CONFIRMED, statusOf(it)) }
            assertEquals(1, txDao.getActiveByAccount(ACC).size)
        }

    @Test
    fun `refused entry writes nothing`() =
        runTest {
            seed(failedLog("log-1"))

            val result = book(booking(timestamp = 1L))

            assertEquals(BookInboxResult.Refused(TransactionError.InvalidTimestamp), result)
            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
        }

    @Test
    fun `a soft-deleted category refuses the booking and writes nothing`() =
        runTest {
            seed(failedLog("log-1"))
            dbRule.db.categoryDao().insert(category(id = "cat-gone", deletedAt = POST))

            val result = book(booking(categoryId = "cat-gone"))

            assertEquals(BookInboxResult.Refused(TransactionError.CategoryNotFound), result)
            assertTrue(txDao.getActiveByAccount(ACC).isEmpty())
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
            assertEquals(1_000_000L, balanceOf(ACC))
        }

    @Test
    fun `proven covered returns CoveredByAdjustment and writes nothing`() =
        runTest {
            seed(failedLog("log-1"))
            txDao.insert(transaction(id = "bank", timestamp = POST + HOUR, postBalance = 900_000L))
            txDao.insert(adjustment("adj", ACC, POST + 2 * HOUR))

            val result = book(booking(resolvedAccountId = ACC))

            assertEquals(BookInboxResult.CoveredByAdjustment, result)
            assertEquals(2, txDao.getActiveByAccount(ACC).size)
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
        }

    @Test
    fun `proven check is skipped on another account`() =
        runTest {
            seed(failedLog("log-1"), accounts = listOf(ACC, OTHER, THIRD))
            txDao.insert(transaction(id = "bank", timestamp = POST + HOUR, postBalance = 900_000L))
            txDao.insert(adjustment("adj", ACC, POST + 2 * HOUR))
            txDao.insert(adjustment("adj-other", OTHER, POST + 2 * HOUR))

            val needs = book(booking(accountId = OTHER, resolvedAccountId = ACC))
            assertEquals(
                BookInboxResult.NeedsAdjustmentConfirmation(AdjustmentToken(OTHER, POST, POST + 2 * HOUR)),
                needs,
            )

            assertEquals(BookInboxResult.Booked, book(booking(accountId = THIRD, resolvedAccountId = ACC)))
            assertEquals(1, txDao.getActiveByAccount(THIRD).size)
        }

    @Test
    fun `later adjustment asks for confirmation then books with the token`() =
        runTest {
            seed(failedLog("log-1"))
            txDao.insert(adjustment("adj", ACC, POST + HOUR))

            val asked = book(booking()) as BookInboxResult.NeedsAdjustmentConfirmation
            assertEquals(AdjustmentToken(ACC, POST, POST + HOUR), asked.token)
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))

            assertEquals(BookInboxResult.Booked, book(booking(), asked.token))
            assertEquals(NotificationStatus.CONFIRMED, statusOf("log-1"))
        }

    @Test
    fun `token for another account asks again`() =
        runTest {
            seed(failedLog("log-1"), accounts = listOf(ACC, OTHER))
            txDao.insert(adjustment("adj", ACC, POST + HOUR))
            txDao.insert(adjustment("adj-other", OTHER, POST + HOUR))
            val otherToken = AdjustmentToken(OTHER, POST, POST + HOUR)

            val result = book(booking(accountId = ACC), otherToken)

            assertEquals(
                BookInboxResult.NeedsAdjustmentConfirmation(AdjustmentToken(ACC, POST, POST + HOUR)),
                result,
            )
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
        }

    @Test
    fun `adjustment newer than the token asks again`() =
        runTest {
            seed(failedLog("log-1"))
            txDao.insert(adjustment("adj", ACC, POST + HOUR))
            val token = (book(booking()) as BookInboxResult.NeedsAdjustmentConfirmation).token
            txDao.insert(adjustment("adj-newer", ACC, POST + 2 * HOUR))

            val result = book(booking(), token)

            assertEquals(
                BookInboxResult.NeedsAdjustmentConfirmation(AdjustmentToken(ACC, POST, POST + 2 * HOUR)),
                result,
            )
            assertEquals(NotificationStatus.FAILED, statusOf("log-1"))
        }

    @Test
    fun `invalid time skips the proven check and counts adjustments after the entered time`() =
        runTest {
            seed(failedLog("log-1", FAIL_INVALID_TIME, postTime = 0L))
            txDao.insert(transaction(id = "bank", timestamp = POST + HOUR, postBalance = 900_000L))
            txDao.insert(adjustment("adj", ACC, POST + 2 * HOUR))

            val result = book(booking(timestamp = POST, resolvedAccountId = ACC))

            assertEquals(
                BookInboxResult.NeedsAdjustmentConfirmation(AdjustmentToken(ACC, POST, POST + 2 * HOUR)),
                result,
            )
        }

    @Test
    fun `dismiss keeps FAILED with user_dismissed and the item leaves the inbox`() =
        runTest {
            seed(failedLog("log-1", FAIL_NO_RULE_MATCHED))
            assertNotNull(repo.getItem("log-1"))

            assertTrue(dismiss("log-1", listOf("log-1")))

            val log = logDao.getById("log-1")
            assertEquals(NotificationStatus.FAILED, log?.status)
            assertEquals(FAIL_USER_DISMISSED, log?.failureReason)
            assertNull(repo.getItem("log-1"))
        }

    @Test
    fun `dismiss of a handled log returns false`() =
        runTest {
            seed(failedLog("log-1"))
            dbRule.execSql("UPDATE notification_logs SET status = 'CONFIRMED' WHERE id = 'log-1'")

            assertFalse(dismiss("log-1", listOf("log-1")))

            assertEquals(NotificationStatus.CONFIRMED, statusOf("log-1"))
            assertEquals(FAIL_SETTLE_ERROR, logDao.getById("log-1")?.failureReason)
        }

    @Test
    fun `dismiss covers every copy of a group and every inbox reason`() =
        runTest {
            val reasons = INBOX_REASONS.toList()
            seed(
                *reasons
                    .filter { it != FAIL_NO_RULE_MATCHED }
                    .map { failedLog("log-$it", it) }
                    .toTypedArray(),
                failedLog("copy-a", FAIL_NO_RULE_MATCHED, POST - HOUR),
                failedLog("copy-b", FAIL_NO_RULE_MATCHED),
            )

            reasons.forEach { reason ->
                val id = if (reason == FAIL_NO_RULE_MATCHED) "copy-a" else "log-$reason"
                val ids = if (reason == FAIL_NO_RULE_MATCHED) listOf("copy-a", "copy-b") else listOf(id)
                assertTrue("dismiss $reason", dismiss(id, ids))
            }

            assertEquals(FAIL_USER_DISMISSED, logDao.getById("copy-a")?.failureReason)
            assertEquals(FAIL_USER_DISMISSED, logDao.getById("copy-b")?.failureReason)
        }
}
