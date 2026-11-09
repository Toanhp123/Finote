package io.github.toanhp123.finote.data.notification

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.planSettlement
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class NotificationSettlementRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private lateinit var repo: NotificationSettlementRepositoryImpl

    @Before
    fun setUp() {
        val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
        repo = NotificationSettlementRepositoryImpl(dbRule.db, clock)
    }

    private fun log(
        id: String,
        postBalance: Long? = 950_000L,
        postTime: Long = 1_000L,
        status: NotificationStatus = NotificationStatus.PENDING,
    ) = NotificationLog(
        id = id,
        packageName = PKG,
        channelId = null,
        title = "t",
        content = "c",
        contentHash = "h-$id",
        postTime = postTime,
        receivedAt = postTime,
        status = status,
        failureReason = null,
        matchedRuleId = "rule",
        parsed = ParsedFields(50_000L, TransactionType.EXPENSE, "1234", postBalance, "Parsed shop", null),
    )

    private suspend fun seed(vararg logs: NotificationLog) =
        logs.forEach { dbRule.db.notificationLogDao().insert(it.toEntity()) }

    private fun newAccount(type: AccountType = AccountType.BANK) = AccountTarget.New(PKG, "1234", "Vietcombank", type)

    private fun book(
        bank: NotificationLog,
        wallet: NotificationLog? = null,
        target: AccountTarget = newAccount(),
    ) = SettlementDecision.Book(bank, wallet, target, merchant = "Shop", note = null)

    private suspend fun storedLog(id: String) =
        dbRule.db
            .notificationLogDao()
            .getAll()
            .single { it.id == id }

    private suspend fun onlyAccount() = repo.getAccounts().single()

    @Test
    fun `book creates account and transaction`() =
        runTest {
            val bank = log("b1")
            seed(bank)

            repo.book(book(bank), "tx-1", NOW)

            val acc = onlyAccount()
            assertEquals("Vietcombank ••1234", acc.name)
            assertEquals(AccountType.BANK, acc.type)
            assertEquals("bank", acc.icon)
            assertEquals("VND", acc.currency)
            assertEquals(0xFF546E7A.toInt(), acc.color)
            assertTrue(acc.isInitialized)
            assertEquals(1_000_000L, acc.initialBalance)
            assertEquals(950_000L, acc.currentBalance)
            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertEquals(acc.id, tx.accountId)
            assertEquals(50_000L, tx.amount)
            assertEquals(0L, tx.feeAmount)
            assertEquals(TransactionType.EXPENSE, tx.type)
            assertEquals(TransactionSource.NOTIFICATION_PARSER, tx.source)
            assertEquals(1_000L, tx.timestamp)
            assertEquals(950_000L, tx.postBalance)
            assertEquals("b1", tx.notificationLogId)
            assertEquals("Shop", tx.merchantName)
            assertEquals("", tx.note)
            assertEquals(NOW, tx.createdAt)
            val stored = storedLog("b1")
            assertEquals(NotificationStatus.CONFIRMED, stored.status)
            assertEquals("tx-1", stored.linkedTransactionId)
        }

    @Test
    fun `book pair dedups wallet log`() =
        runTest {
            val bank = log("b1")
            val wallet = log("w1")
            seed(bank, wallet)

            repo.book(book(bank, wallet), "tx-1", NOW)

            val stored = storedLog("w1")
            assertEquals(NotificationStatus.DEDUPLICATED, stored.status)
            assertEquals("tx-1", stored.linkedTransactionId)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("b1").status)
            assertEquals(
                "Shop",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")!!
                    .merchantName,
            )
        }

    @Test
    fun `two New for one key make one account`() =
        runTest {
            val a = log("a")
            val b = log("b", postTime = 2_000L, postBalance = 900_000L)
            seed(a, b)

            repo.book(book(a), "tx-a", NOW)
            repo.book(book(b), "tx-b", NOW)

            val acc = onlyAccount()
            assertEquals(
                acc.id,
                dbRule.db
                    .transactionDao()
                    .getById("tx-b")!!
                    .accountId,
            )
            assertEquals(900_000L, acc.currentBalance)
        }

    @Test
    fun `no post balance leaves account uninitialized and a later one initializes it`() =
        runTest {
            val first = log("a", postBalance = null)
            val second = log("b", postTime = 2_000L, postBalance = 900_000L)
            seed(first, second)

            repo.book(book(first), "tx-a", NOW)
            val before = onlyAccount()
            assertFalse(before.isInitialized)
            assertEquals(0L, before.initialBalance)
            assertEquals(-50_000L, before.currentBalance)

            repo.book(book(second), "tx-b", NOW)

            val after = onlyAccount()
            assertTrue(after.isInitialized)
            assertEquals(1_000_000L, after.initialBalance)
            assertEquals(900_000L, after.currentBalance)
        }

    @Test
    fun `credit card skips cold start`() =
        runTest {
            val bank = log("b1")
            seed(bank)

            repo.book(book(bank, target = newAccount(AccountType.CREDIT_CARD)), "tx-1", NOW)

            val acc = onlyAccount()
            assertFalse(acc.isInitialized)
            assertEquals(0L, acc.initialBalance)
            assertEquals(-50_000L, acc.currentBalance)
        }

    @Test
    fun `user-initialized account keeps its balance`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG, accountNumberMask = "1234"))
            val bank = log("b1", postBalance = 123L)
            seed(bank)

            repo.book(book(bank, target = AccountTarget.Existing("acc-1")), "tx-1", NOW)

            val acc = onlyAccount()
            assertEquals(1_000_000L, acc.initialBalance)
            assertEquals(950_000L, acc.currentBalance)
        }

    @Test
    fun `pair update that hits one row rolls back`() =
        runTest {
            val bank = log("b1")
            val wallet = log("w1", status = NotificationStatus.CONFIRMED)
            seed(bank, wallet)

            try {
                repo.book(book(bank, wallet), "tx-1", NOW)
                fail("expected IllegalStateException")
            } catch (_: IllegalStateException) {
                // expected
            }

            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertTrue(repo.getAccounts().isEmpty())
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    private suspend fun insertArchivedAccount() =
        dbRule.db
            .accountDao()
            .insert(account(bankPackageName = PKG, accountNumberMask = "1234").copy(isArchived = true))

    private suspend fun assertBookRollsBack(
        bank: NotificationLog,
        target: AccountTarget,
    ) {
        try {
            repo.book(book(bank, target = target), "tx-1", NOW)
            fail("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
        assertNull(dbRule.db.transactionDao().getById("tx-1"))
        assertEquals(NotificationStatus.PENDING, storedLog(bank.id).status)
    }

    @Test
    fun `archived Existing account is not booked into`() =
        runTest {
            insertArchivedAccount()
            val bank = log("b1")
            seed(bank)

            assertBookRollsBack(bank, AccountTarget.Existing("acc-1"))
        }

    @Test
    fun `New whose key now matches an archived account is not booked`() =
        runTest {
            insertArchivedAccount()
            val bank = log("b1")
            seed(bank)

            assertBookRollsBack(bank, newAccount())

            assertEquals(1, repo.getAccounts().size)
        }

    @Test
    fun `book into an account ignored after planning rolls back`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG, accountNumberMask = "1234"))
            val bank = log("b1")
            seed(bank)
            val planned = book(bank, target = AccountTarget.Existing("acc-1"))
            dbRule.db.accountDao().update(
                dbRule.db
                    .accountDao()
                    .getById("acc-1")!!
                    .copy(isIgnored = true),
            )

            try {
                repo.book(planned, "tx-1", NOW)
                fail("expected IllegalStateException")
            } catch (_: IllegalStateException) {
                // expected
            }

            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    @Test
    fun `book into an account merged away after planning rolls back`() =
        runTest {
            val accountDao = dbRule.db.accountDao()
            accountDao.insert(account(id = "b", bankPackageName = PKG, accountNumberMask = "1234"))
            accountDao.insert(account(id = "c", bankPackageName = PKG, accountNumberMask = "9999"))
            val bank = log("b1")
            seed(bank)
            val planned = book(bank, target = AccountTarget.Existing("b"))
            accountDao.update(accountDao.getById("b")!!.copy(deletedAt = NOW, mergedIntoId = "c"))

            try {
                repo.book(planned, "tx-1", NOW)
                fail("expected IllegalStateException")
            } catch (_: IllegalStateException) {
                // expected
            }
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)

            val replanned =
                planSettlement(repo.getPendingLogs(), emptyList(), repo.getAccounts(), repo.getRulesById(), 1_000_000L)
            val decision = replanned.single() as SettlementDecision.Book
            assertEquals(AccountTarget.Existing("c"), decision.account)
            repo.book(decision, "tx-2", NOW)
            assertEquals(
                "c",
                dbRule.db
                    .transactionDao()
                    .getById("tx-2")!!
                    .accountId,
            )
        }

    @Test
    fun `ignore marks both logs IGNORED_ACCOUNT`() =
        runTest {
            val a = log("a")
            val b = log("b")
            seed(a, b)

            assertTrue(repo.ignore(SettlementDecision.Ignore(a, b)))

            listOf("a", "b").forEach {
                val stored = storedLog(it)
                assertEquals(NotificationStatus.IGNORED_ACCOUNT, stored.status)
                assertNull(stored.linkedTransactionId)
                assertNull(stored.failureReason)
            }
            assertFalse(repo.ignore(SettlementDecision.Ignore(a, b)))
        }

    @Test
    fun `two New with a null mask make one account named by the bank`() =
        runTest {
            val a = log("a")
            val b = log("b", postTime = 2_000L)
            seed(a, b)
            val target = AccountTarget.New(PKG, null, "Momo", AccountType.WALLET)

            repo.book(book(a, target = target), "tx-a", NOW)
            repo.book(book(b, target = target), "tx-b", NOW)

            assertEquals("Momo", onlyAccount().name)
        }

    @Test
    fun `deleted notification transaction is not booked again`() =
        runTest {
            val bank = log("b1")
            seed(bank)
            repo.book(book(bank), "tx-1", NOW)
            val dao = dbRule.db.transactionDao()
            dao.update(dao.getById("tx-1")!!.copy(deletedAt = NOW))

            assertEquals(NotificationStatus.CONFIRMED, storedLog("b1").status)
            assertTrue(repo.getPendingLogs().isEmpty())
        }

    @Test
    fun `fail marks both logs`() =
        runTest {
            val a = log("a")
            val b = log("b")
            seed(a, b)

            assertTrue(repo.fail(SettlementDecision.Fail(a, b, "no account")))

            assertEquals(NotificationStatus.FAILED, storedLog("a").status)
            assertEquals("no account", storedLog("a").failureReason)
            assertEquals(NotificationStatus.FAILED, storedLog("b").status)
            assertFalse(repo.fail(SettlementDecision.Fail(a, b, "again")))
        }

    @Test
    fun `markRepost links a pending log and refuses a non-pending one`() =
        runTest {
            seed(log("a"), log("done", status = NotificationStatus.CONFIRMED))

            assertTrue(repo.markRepost("a", "tx-9"))
            assertEquals(NotificationStatus.DEDUPLICATED, storedLog("a").status)
            assertEquals("tx-9", storedLog("a").linkedTransactionId)
            assertFalse(repo.markRepost("done", "tx-9"))
        }

    @Test
    fun `pending and settled reads map the log`() =
        runTest {
            seed(
                log("late", postTime = 3_000L),
                log("early", postTime = 1_000L),
                log("old", postTime = 500L, status = NotificationStatus.CONFIRMED),
                log("new", postTime = 2_000L, status = NotificationStatus.CONFIRMED),
                log("spam", postTime = 2_000L, status = NotificationStatus.IGNORED_SPAM),
            )
            val linked = storedLog("new").copy(id = "linked", contentHash = "h-linked", linkedTransactionId = "tx-1")
            dbRule.db.notificationLogDao().insert(linked)

            assertEquals(listOf("early", "late"), repo.getPendingLogs().map { it.id })
            val settled = repo.getSettledLogsSince(1_000L)
            assertEquals(listOf("linked", "new"), settled.map { it.id }.sorted())
            assertEquals("tx-1", settled.single { it.id == "linked" }.linkedTransactionId)
            val parsed = settled.first().parsed
            assertNotNull(parsed)
            assertEquals(50_000L, parsed!!.amount)
        }

    @Test
    fun `getRulesById drops a broken rule`() =
        runTest {
            val dao = dbRule.db.parserRuleDao()
            dao.insert(parserRuleEntity(id = "bad", ignoreKeywords = "x"))
            dao.insert(parserRuleEntity(id = "good"))

            assertEquals(setOf("good"), repo.getRulesById().keys)
        }

    private companion object {
        const val PKG = "com.test.bank"
        const val NOW = 5_000L
    }
}
