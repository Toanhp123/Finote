package io.github.toanhp123.finote.data.notification.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.local.entity.AccountIdentifierEntity
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.repository.AccountMergeRepositoryImpl
import io.github.toanhp123.finote.data.category.local.entity.CategoryKeywordRuleEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.data.notification.mapper.toDomain
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.data.notification.parserRuleEntity
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.model.defaultKind
import io.github.toanhp123.finote.domain.account.service.balanceDriftKey
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.common.StorageException
import io.github.toanhp123.finote.domain.common.normalizeKeyword
import io.github.toanhp123.finote.domain.notification.error.SettlementError
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.service.planSettlement
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.SettlePendingLogsUseCase
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
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

@Suppress("LargeClass") // why: one class per repository; the pairing cases share its log and account helpers
@RunWith(AndroidJUnit4::class)
class NotificationSettlementRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule(seedSystemCategories = true)

    private lateinit var repo: NotificationSettlementRepositoryImpl

    @Before
    fun setUp() {
        val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
        repo = NotificationSettlementRepositoryImpl(dbRule.db, clock)
    }

    @Suppress("LongParameterList") // why: one named default per log field the pairing tests vary
    private fun log(
        id: String,
        postBalance: Long? = 950_000L,
        postTime: Long = 1_000L, // offset from T0, so it stays inside the ledger time range
        status: NotificationStatus = NotificationStatus.PENDING,
        amount: Long = 50_000L,
        type: TransactionType = TransactionType.EXPENSE,
        mask: String? = "1234",
        packageName: String = PKG,
    ) = NotificationLog(
        id = id,
        packageName = packageName,
        channelId = null,
        title = "t",
        content = "c",
        contentHash = "h-$id",
        postTime = T0 + postTime,
        receivedAt = T0 + postTime,
        status = status,
        failureReason = null,
        matchedRuleId = "rule",
        parsed = ParsedFields(amount, type, mask, postBalance, "Parsed shop", null),
    )

    private suspend fun seed(vararg logs: NotificationLog) =
        logs.forEach { dbRule.db.notificationLogDao().insert(it.toEntity()) }

    private fun newAccount(
        type: AccountType = AccountType.BANK,
        mask: String? = "1234",
    ) = AccountTarget.New(PKG, mask, "Vietcombank", type, type.defaultKind())

    /** The writer resolves the account again from the rule, so a New target also names the rule behind it. */
    private fun book(
        bank: NotificationLog,
        wallet: NotificationLog? = null,
        target: AccountTarget = newAccount(),
    ): SettlementDecision.Book {
        val new = target as? AccountTarget.New
        val rule =
            testRule(
                id = "rule",
                packageName = new?.packageName ?: PKG,
                bankName = new?.bankName ?: "Vietcombank",
                accountType = new?.type ?: AccountType.BANK,
            )
        return SettlementDecision.Book(bank, wallet, target, merchant = "Shop", note = null, rule = rule)
    }

    /** Books the way the use case does: an error rolls the whole write back. */
    private suspend fun bookInTx(
        decision: SettlementDecision.Book,
        transactionId: String = "tx-1",
    ): SettlementError? {
        val outcome =
            RoomTransactionRunner(dbRule.db).write {
                repo.book(decision, transactionId, NOW)?.let { Outcome.Err(it) } ?: Outcome.Ok(Unit)
            }
        return (outcome as? Outcome.Err)?.error
    }

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
            assertFalse(acc.isInitialized) // provisional: one balance row cannot lock the start
            assertEquals(1_000_000L, acc.initialBalance)
            assertEquals(950_000L, acc.currentBalance)
            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertEquals(acc.id, tx.accountId)
            assertEquals(50_000L, tx.amount)
            assertEquals(0L, tx.feeAmount)
            assertEquals(TransactionType.EXPENSE, tx.type)
            assertEquals(TransactionSource.NOTIFICATION_PARSER, tx.source)
            assertEquals(T0 + 1_000L, tx.timestamp)
            assertEquals(950_000L, tx.postBalance)
            assertEquals("b1", tx.notificationLogId)
            assertEquals("Shop", tx.merchantName)
            assertEquals("", tx.note)
            assertEquals(NOW, tx.createdAt)
            val stored = storedLog("b1")
            assertEquals(NotificationStatus.CONFIRMED, stored.status)
            assertEquals("tx-1", stored.linkedTransactionId)
        }

    private suspend fun addRule(
        keyword: String,
        categoryId: String,
        merchantName: String? = null,
    ) = dbRule.db.keywordRuleDao().upsert(
        CategoryKeywordRuleEntity(
            id = "rule-$keyword",
            keyword = keyword,
            normalizedKeyword = normalizeKeyword(keyword),
            matchType = KeywordMatchType.CONTAINS,
            normalizedMerchantName = merchantName,
            categoryId = categoryId,
            priority = 0,
            isUserDefined = true,
            isActive = true,
            version = 1,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )

    @Test
    fun `book sets category and rule merchant name`() =
        runTest {
            val bank = log("b1")
            seed(bank)
            addRule("shop", "sys_food_coffee", merchantName = "Coffee House")

            repo.book(book(bank), "tx-1", NOW)

            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertEquals("sys_food_coffee", tx.categoryId)
            assertEquals("Coffee House", tx.merchantName)
        }

    @Test
    fun `book keeps the parsed merchant when the rule has no name`() =
        runTest {
            val bank = log("b1")
            seed(bank)
            addRule("shop", "sys_food_coffee")

            repo.book(book(bank), "tx-1", NOW)

            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertEquals("sys_food_coffee", tx.categoryId)
            assertEquals("Shop", tx.merchantName)
        }

    @Test
    fun `book uses the wallet merchant of a pair`() =
        runTest {
            val bank = log("b1")
            val wallet = log("w1")
            seed(bank, wallet)
            addRule("highlands", "sys_food_coffee", merchantName = "Highlands Coffee")

            repo.book(book(bank, wallet).copy(merchant = "HIGHLANDS COFFEE 12"), "tx-1", NOW)

            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertEquals("sys_food_coffee", tx.categoryId)
            assertEquals("Highlands Coffee", tx.merchantName)
        }

    @Test
    fun `book leaves category null when no rule matches`() =
        runTest {
            val bank = log("b1")
            seed(bank)
            addRule("other", "sys_food_coffee", merchantName = "Other")

            repo.book(book(bank), "tx-1", NOW)

            val tx = dbRule.db.transactionDao().getById("tx-1")!!
            assertNull(tx.categoryId)
            assertEquals("Shop", tx.merchantName)
        }

    @Test
    fun `a paired transfer has no category`() =
        runTest {
            addRule("shop", "sys_food_coffee")
            seed(debit(), credit())
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
            repo.book(book(credit(), target = newAccount(mask = "2222")), "tx-2", NOW)

            val transfer = liveRows().single()

            assertEquals(TransactionType.TRANSFER, transfer.type)
            assertNull(transfer.categoryId)
        }

    @Test
    fun `auto-created account goes after the user's order`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "user-a", sortOrder = 0))
            dbRule.db.accountDao().insert(account(id = "user-b", sortOrder = 5))
            val bank = log("b1")
            seed(bank)

            repo.book(book(bank), "tx-1", NOW)

            // The user accounts have no bank package, so getAccounts() leaves them out and only the new one is left.
            assertEquals(6, onlyAccount().sortOrder)
        }

    @Test
    fun `book refuses a post time outside the ledger range and writes nothing`() =
        runTest {
            val bank = log("b1", postTime = 1_000L - T0) // epoch 1970, before the lower bound
            seed(bank)

            val error = bookInTx(book(bank))

            assertEquals(SettlementError.Refused(TransactionError.InvalidTimestamp), error)
            assertTrue(repo.getAccounts().isEmpty())
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
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
    fun `no post balance leaves account uninitialized and a later one sets its provisional start`() =
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
            assertFalse(after.isInitialized) // a single balance row is still provisional
            assertEquals(1_000_000L, after.initialBalance)
            assertEquals(900_000L, after.currentBalance)
        }

    @Test
    fun `cold start then drift is null on equal timestamps`() =
        runTest {
            dbRule.db.accountDao().insert(
                account(bankPackageName = PKG).copy(isInitialized = false),
            )
            // Same timestamp as the booked row; it is created later, so the shared order puts it after the anchor.
            dbRule.db.transactionDao().insert(transaction(id = "manual", accountId = "acc-1", timestamp = T0 + 1_000L))
            val bank = log("b1")
            seed(bank)

            repo.book(book(bank, target = AccountTarget.Existing("acc-1")), "tx-1", NOW)

            val stored = checkNotNull(dbRule.db.accountDao().getById("acc-1"))
            assertFalse(stored.isInitialized) // provisional: one balance row cannot lock the start
            val rows =
                dbRule.db
                    .transactionDao()
                    .getActiveByAccount("acc-1")
                    .map { it.toDomain() }
            assertNull(balanceDriftKey(stored.toDomain(), rows))
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
            dbRule.db.accountDao().insert(account(bankPackageName = PKG))
            val bank = log("b1", postBalance = 123L)
            seed(bank)

            repo.book(book(bank, target = AccountTarget.Existing("acc-1")), "tx-1", NOW)

            val acc = onlyAccount()
            assertEquals(1_000_000L, acc.initialBalance)
            assertEquals(950_000L, acc.currentBalance)
        }

    @Test
    fun `pair update that hits one row returns LogNotPending and rolls back`() =
        runTest {
            val bank = log("b1")
            val wallet = log("w1", status = NotificationStatus.CONFIRMED)
            seed(bank, wallet)

            assertEquals(SettlementError.LogNotPending, bookInTx(book(bank, wallet)))

            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertTrue(repo.getAccounts().isEmpty())
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    private suspend fun insertArchivedAccount() =
        dbRule.db
            .accountDao()
            .insert(account(bankPackageName = PKG).copy(isArchived = true))

    private suspend fun assertBookRollsBack(
        bank: NotificationLog,
        target: AccountTarget,
    ) {
        assertEquals(SettlementError.AccountUnusable, bookInTx(book(bank, target = target)))
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
    fun `a stale Existing target is not read and the fresh answer books`() =
        runTest {
            val bank = log("b1")
            seed(bank)

            assertNull(bookInTx(book(bank, target = AccountTarget.Existing("missing"))))

            assertEquals(
                onlyAccount().id,
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.accountId,
            )
            assertEquals(onlyAccount().id, ownerOf("1234"))
        }

    @Test
    fun `book into an account ignored after planning rolls back`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG))
            val bank = log("b1")
            seed(bank)
            val planned = book(bank, target = AccountTarget.Existing("acc-1"))
            dbRule.db.accountDao().update(
                dbRule.db
                    .accountDao()
                    .getById("acc-1")!!
                    .copy(isIgnored = true),
            )

            assertEquals(SettlementError.AccountUnusable, bookInTx(planned))

            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    @Test
    fun `book into an account merged away after planning lands on the merge target`() =
        runTest {
            val accountDao = dbRule.db.accountDao()
            val identifierDao = dbRule.db.accountIdentifierDao()
            accountDao.insert(account(id = "b", bankPackageName = PKG))
            accountDao.insert(account(id = "c", bankPackageName = PKG))
            identifierDao.insert(AccountIdentifierEntity(PKG, IdentifierKind.ACCOUNT, "1234", "b"))
            identifierDao.insert(AccountIdentifierEntity(PKG, IdentifierKind.ACCOUNT, "9999", "c"))
            val bank = log("b1")
            seed(bank)
            val planned = book(bank, target = AccountTarget.Existing("b"))
            AccountMergeRepositoryImpl(dbRule.db, Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC))
                .merge("b", "c", NOW)

            assertNull(bookInTx(planned))

            assertEquals(
                "c",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.accountId,
            )
            assertEquals(NotificationStatus.CONFIRMED, storedLog("b1").status)
        }

    @Test
    fun `source mask lands on target after merge`() =
        runTest {
            val accountDao = dbRule.db.accountDao()
            accountDao.insert(account(id = "b", bankPackageName = PKG))
            accountDao.insert(account(id = "c", bankPackageName = PKG))
            insertKey("1234", "b")
            insertKey("9999", "c")
            AccountMergeRepositoryImpl(dbRule.db, Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC))
                .merge("b", "c", NOW)
            val bank = log("b1")
            seed(bank)

            assertNull(bookInTx(book(bank, target = newAccount())))

            assertEquals("c", accountOfTx("tx-1"))
            assertEquals("c", ownerOf("1234"))
            assertEquals(listOf("c"), repo.getAccounts().filter { it.deletedAt == null }.map { it.id })
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
            val a = log("a", mask = null)
            val b = log("b", postTime = 2_000L, mask = null)
            seed(a, b)
            val target = AccountTarget.New(PKG, null, "Momo", AccountType.WALLET, IdentifierKind.WALLET)

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
    fun `fail of a pair gives the bank the reason and the wallet partner_failed`() =
        runTest {
            val a = log("a")
            val b = log("b")
            seed(a, b)

            assertTrue(repo.fail(SettlementDecision.Fail(a, b, "no account")))

            assertEquals(NotificationStatus.FAILED, storedLog("a").status)
            assertEquals("no account", storedLog("a").failureReason)
            assertEquals(NotificationStatus.FAILED, storedLog("b").status)
            assertEquals("partner_failed", storedLog("b").failureReason)
            assertFalse(repo.fail(SettlementDecision.Fail(a, b, "again")))
        }

    @Test
    fun `fail without a partner gives the log the reason`() =
        runTest {
            val a = log("a")
            seed(a)

            assertTrue(repo.fail(SettlementDecision.Fail(a, null, "no account")))

            assertEquals("no account", storedLog("a").failureReason)
        }

    @Test
    fun `fail of a pair writes both or neither`() =
        runTest {
            val a = log("a")
            val b = log("b")
            seed(a, b)
            // The wallet update is the second write; making it throw must undo the bank update.
            dbRule.execSql(
                "CREATE TRIGGER wallet_fails BEFORE UPDATE ON notification_logs " +
                    "WHEN NEW.failureReason = 'partner_failed' BEGIN SELECT RAISE(ABORT, 'boom'); END",
            )

            val thrown = runCatching { repo.fail(SettlementDecision.Fail(a, b, "no account")) }.exceptionOrNull()

            assertTrue(thrown is StorageException)
            assertEquals(NotificationStatus.PENDING, storedLog("a").status)
            assertNull(storedLog("a").failureReason)
            assertEquals(NotificationStatus.PENDING, storedLog("b").status)
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
            val settled = repo.getSettledLogsSince(T0 + 1_000L)
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

    private suspend fun accountOf(mask: String) =
        repo.getIdentifiers().single { it.mask == mask }.let { key ->
            repo.getAccounts().single { it.id == key.accountId }
        }

    private suspend fun liveRows() =
        dbRule.db
            .transactionDao()
            .observeTransactions(null)
            .first()

    private fun debit(postBalance: Long? = 1_500_000L) = log("d", postBalance, 1_000L, amount = 500_000L, mask = "1111")

    private fun credit(postBalance: Long? = 700_000L) =
        log(
            id = "c",
            postBalance = postBalance,
            postTime = 1_000L + 5 * MINUTE,
            amount = 500_000L,
            type = TransactionType.INCOME,
            mask = "2222",
        )

    private fun walletCredit(postTime: Long) =
        log(
            id = "w",
            postBalance = 700_000L,
            postTime = postTime,
            amount = 500_000L,
            type = TransactionType.INCOME,
            mask = null,
            packageName = MOMO,
        )

    /** A 500_000 debit on account 1111, then a 500_000 credit on account 2222 five minutes later. */
    private suspend fun bookDebitThenCredit() {
        seed(debit(), credit())
        repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
        repo.book(book(credit(), target = newAccount(mask = "2222")), "tx-2", NOW)
    }

    @Test
    fun `debit then credit 5 min later becomes one transfer`() =
        runTest {
            seed(debit(), credit())
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
            val partnerBefore = dbRule.db.transactionDao().getById("tx-1")!!
            repo.book(book(credit(), target = newAccount(mask = "2222")), "tx-2", NOW)

            val a = accountOf("1111")
            val b = accountOf("2222")
            val transfer = liveRows().single()
            assertEquals(TransactionType.TRANSFER, transfer.type)
            assertEquals(a.id, transfer.accountId)
            assertEquals(b.id, transfer.toAccountId)
            assertEquals(500_000L, transfer.amount)
            assertEquals(1_500_000L, transfer.postBalance)
            assertEquals(700_000L, transfer.toPostBalance)
            assertEquals(T0 + 1_000L + 5 * MINUTE, transfer.toTimestamp)
            assertNull(transfer.notificationLogId)
            listOf("tx-1", "tx-2").forEach {
                val original = dbRule.db.transactionDao().getById(it)!!
                assertNotNull(original.deletedAt)
                assertEquals(transfer.id, original.pairedIntoId)
            }
            // The partner keeps every stored field (updatedAt included) except the two pair fields.
            assertEquals(
                partnerBefore.copy(deletedAt = NOW, pairedIntoId = transfer.id),
                dbRule.db.transactionDao().getById("tx-1"),
            )
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("d").status)
            assertEquals("tx-1", storedLog("d").linkedTransactionId)
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("c").status)
            assertEquals("tx-2", storedLog("c").linkedTransactionId)
            // Same balances as a manual transfer of 500_000 from a (2_000_000 start) to b (200_000 start).
            assertEquals(2_000_000L, a.initialBalance)
            assertEquals(1_500_000L, a.currentBalance)
            assertEquals(200_000L, b.initialBalance)
            assertEquals(700_000L, b.currentBalance)
        }

    @Test
    fun `second row is never live`() =
        runTest {
            bookDebitThenCredit()

            assertNotNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-2")!!
                    .deletedAt,
            )
        }

    @Test
    fun `a debit that arrives after its credit still sends from the debit account`() =
        runTest {
            seed(debit(), credit())
            repo.book(book(credit(), target = newAccount(mask = "2222")), "tx-2", NOW)
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)

            val transfer = liveRows().single()
            assertEquals(accountOf("1111").id, transfer.accountId)
            assertEquals(accountOf("2222").id, transfer.toAccountId)
            assertEquals(T0 + 1_000L, transfer.timestamp)
        }

    /** A log due at the clock of [runSettlement], [minutesAgo] before it. */
    private fun dueLog(
        id: String,
        minutesAgo: Long,
        type: TransactionType,
        mask: String?,
        rule: String = "rule",
        packageName: String = PKG,
    ) = log(
        id = id,
        postBalance = 800_000L,
        postTime = SETTLE_NOW - T0 - minutesAgo * MINUTE,
        amount = 500_000L,
        type = type,
        mask = mask,
        packageName = packageName,
    ).copy(matchedRuleId = rule, content = "text-$id") // distinct text, or the planner reads it as a repost

    private suspend fun runSettlement(): Boolean {
        val clock = Clock.fixed(Instant.ofEpochMilli(SETTLE_NOW), ZoneOffset.UTC)
        val dao = dbRule.db.parserRuleDao()
        dao.insert(parserRuleEntity(id = "rule", packageName = PKG))
        dao.insert(parserRuleEntity(id = "wallet-rule", packageName = MOMO, accountType = "WALLET"))
        return SettlePendingLogsUseCase(repo, RoomTransactionRunner(dbRule.db), clock)()
    }

    private suspend fun insertKey(
        mask: String,
        accountId: String,
    ) = dbRule.db.accountIdentifierDao().insert(AccountIdentifierEntity(PKG, IdentifierKind.ACCOUNT, mask, accountId))

    private suspend fun ownerOf(mask: String) = repo.getIdentifiers().single { it.mask == mask }.accountId

    private suspend fun accountOfTx(id: String) =
        dbRule.db
            .transactionDao()
            .getById(id)
            ?.accountId

    /** Plans the pending logs against one snapshot, as the use case does before it books. */
    private suspend fun planPending() =
        planSettlement(
            repo.getPendingLogs(),
            emptyList(),
            repo.getAccounts(),
            repo.getIdentifiers(),
            repo.getRulesById(),
            T0 + 1_000_000L,
        )

    @Test
    fun `two masks planned onto one N-05 account attach only the first`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG))
            seed(dueLog("d1", 40, TransactionType.EXPENSE, "1111"), dueLog("d2", 35, TransactionType.EXPENSE, "3333"))

            assertFalse(runSettlement())

            assertEquals("acc-1", ownerOf("1111"))
            val second = ownerOf("3333")
            assertTrue(second != "acc-1")
            assertEquals(setOf("acc-1", second), liveRows().map { it.accountId }.toSet())
            assertEquals(2, repo.getAccounts().size)
        }

    @Test
    fun `two mask-less logs planned New make one account`() =
        runTest {
            seed(dueLog("d1", 40, TransactionType.EXPENSE, null), dueLog("d2", 35, TransactionType.EXPENSE, null))

            assertFalse(runSettlement())

            val only = onlyAccount()
            assertEquals(listOf(only.id, only.id), liveRows().map { it.accountId })
            assertTrue(repo.getIdentifiers().isEmpty())
        }

    @Test
    fun `free identifier is re-pointed through New`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "old", bankPackageName = PKG, deletedAt = 1L))
            insertKey("1111", "old")
            seed(dueLog("d", 40, TransactionType.EXPENSE, "1111"))

            assertFalse(runSettlement())

            val created = ownerOf("1111")
            assertTrue(created != "old")
            assertEquals(created, liveRows().single().accountId)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("d").status)
        }

    @Test
    fun `free identifier is re-pointed through N-05`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "old", bankPackageName = PKG, deletedAt = 1L))
            dbRule.db.accountDao().insert(account(id = "live", bankPackageName = PKG))
            insertKey("1111", "old")
            seed(dueLog("d", 40, TransactionType.EXPENSE, "1111"))

            assertFalse(runSettlement())

            assertEquals("live", ownerOf("1111"))
            assertEquals("live", liveRows().single().accountId)
        }

    @Test
    fun `account archived after planning stays pending`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG))
            insertKey("1234", "acc-1")
            seed(log("b1"))
            val planned = planPending().single() as SettlementDecision.Book
            dbRule.db.accountDao().update(
                dbRule.db
                    .accountDao()
                    .getById("acc-1")!!
                    .copy(isArchived = true),
            )

            assertEquals(SettlementError.AccountUnusable, bookInTx(planned))

            assertNull(accountOfTx("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    @Test
    fun `account ignored after planning stays pending`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = PKG))
            insertKey("1234", "acc-1")
            seed(log("b1"))
            val planned = planPending().single() as SettlementDecision.Book
            dbRule.db.accountDao().update(
                dbRule.db
                    .accountDao()
                    .getById("acc-1")!!
                    .copy(isIgnored = true),
            )

            assertEquals(SettlementError.AccountUnusable, bookInTx(planned))

            assertNull(accountOfTx("tx-1"))
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    @Test
    fun `same worker run pairs`() =
        runTest {
            seed(
                dueLog("d", 40, TransactionType.EXPENSE, "1111"),
                dueLog("c", 35, TransactionType.INCOME, "2222"),
            )

            assertFalse(runSettlement())

            val transfer = liveRows().single()
            assertEquals(TransactionType.TRANSFER, transfer.type)
            assertEquals(accountOf("1111").id, transfer.accountId)
            assertEquals(accountOf("2222").id, transfer.toAccountId)
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("d").status)
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("c").status)
        }

    @Test
    fun `MoMo top-up from VCB pairs`() =
        runTest {
            val bank = debit()
            val wallet = walletCredit(postTime = 1_000L + 20_000L)
            seed(bank, wallet)

            repo.book(book(bank, target = newAccount(mask = "1111")), "tx-1", NOW)
            repo.book(
                book(wallet, target = AccountTarget.New(MOMO, null, "MoMo", AccountType.WALLET, IdentifierKind.WALLET)),
                "tx-2",
                NOW,
            )

            val transfer = liveRows().single()
            assertEquals(TransactionType.TRANSFER, transfer.type)
            assertEquals(accountOf("1111").id, transfer.accountId)
            assertEquals(AccountType.WALLET, repo.getAccounts().single { it.id == transfer.toAccountId }.type)
        }

    @Test
    fun `MoMo paid from VCB stays one expense`() =
        runTest {
            val wallet = dueLog("w", 40, TransactionType.EXPENSE, null, rule = "wallet-rule", packageName = MOMO)
            seed(dueLog("d", 40, TransactionType.EXPENSE, "1111"), wallet.copy(postTime = wallet.postTime + 10_000L))

            assertFalse(runSettlement())

            assertEquals(TransactionType.EXPENSE, liveRows().single().type)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("d").status)
            assertEquals(NotificationStatus.DEDUPLICATED, storedLog("w").status)
        }

    @Test
    fun `wallet log deduplicated into a paired row keeps its link`() =
        runTest {
            val wallet = walletCredit(postTime = 1_000L + 5 * MINUTE + 10_000L)
            seed(debit(), credit(), wallet)
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)

            repo.book(book(credit(), wallet, newAccount(mask = "2222")), "tx-2", NOW)

            assertEquals(TransactionType.TRANSFER, liveRows().single().type)
            assertEquals(NotificationStatus.DEDUPLICATED, storedLog("w").status)
            assertEquals("tx-2", storedLog("w").linkedTransactionId)
        }

    @Test
    fun `partner account archived after its booking books the new row alone`() =
        runTest {
            seed(debit(), credit())
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
            val accountDao = dbRule.db.accountDao()
            accountDao.update(accountDao.getById(accountOf("1111").id)!!.copy(isArchived = true))

            assertNull(bookInTx(book(credit(), target = newAccount(mask = "2222")), "tx-2"))

            val rows = liveRows()
            assertEquals(listOf("tx-1", "tx-2"), rows.map { it.id }.sorted())
            assertEquals(TransactionType.INCOME, rows.single { it.id == "tx-2" }.type)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("c").status)
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-2")!!
                    .pairedIntoId,
            )
        }

    @Test
    fun `uninitialized receiver gets its start from toPostBalance`() =
        runTest {
            dbRule.db.accountDao().insert(
                account(id = "acc-b", currentBalance = 0L, bankPackageName = PKG)
                    .copy(isInitialized = false),
            )
            insertKey("2222", "acc-b")
            seed(debit(), credit())
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)

            repo.book(book(credit(), target = AccountTarget.Existing("acc-b")), "tx-2", NOW)

            assertEquals(TransactionType.TRANSFER, liveRows().single().type)
            val b = repo.getAccounts().single { it.id == "acc-b" }
            assertFalse(b.isInitialized) // provisional: one balance row cannot lock the start
            assertEquals(200_000L, b.initialBalance)
            assertEquals(700_000L, b.currentBalance)
        }

    @Test
    fun `late receiver row after pairing does not cause drift`() =
        runTest {
            // The receiver starts at 230_000: with the late 30_000 expense the books match the bank's 700_000.
            dbRule.db.accountDao().insert(
                account(id = "acc-b", currentBalance = 230_000L, bankPackageName = PKG),
            )
            val late = log("late", 200_000L, 1_000L + 2 * MINUTE, amount = 30_000L, mask = "2222")
            insertKey("2222", "acc-b")
            seed(debit(), credit(), late)
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
            repo.book(book(credit(), target = AccountTarget.Existing("acc-b")), "tx-2", NOW)

            repo.book(book(late, target = AccountTarget.Existing("acc-b")), "tx-3", NOW)

            assertEquals(setOf(TransactionType.TRANSFER, TransactionType.EXPENSE), liveRows().map { it.type }.toSet())
            val b = repo.getAccounts().single { it.id == "acc-b" }
            assertEquals(700_000L, b.currentBalance)
            val rows =
                dbRule.db
                    .transactionDao()
                    .getActiveByAccount(b.id)
                    .map { it.toDomain() }
            // The drift assertion carries the test: a wrong anchor on the late expense would report 500_000.
            assertNull(balanceDriftKey(b, rows))
        }

    @Test
    fun `failure while pairing rolls back the new row`() =
        runTest {
            dbRule.db.accountDao().insert(
                account(id = "acc-b", currentBalance = 200_000L, bankPackageName = PKG),
            )
            insertKey("2222", "acc-b")
            seed(debit(), credit())
            repo.book(book(debit(), target = newAccount(mask = "1111")), "tx-1", NOW)
            val balancesBefore = repo.getAccounts().associate { it.id to it.currentBalance }

            val thrown =
                runCatching {
                    RoomTransactionRunner(dbRule.db).write<Unit, SettlementError> {
                        repo.book(book(credit(), target = AccountTarget.Existing("acc-b")), "tx-2", NOW)
                        error("boom")
                    }
                }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException)
            assertNull(dbRule.db.transactionDao().getById("tx-2"))
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")!!
                    .deletedAt,
            )
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")!!
                    .pairedIntoId,
            )
            assertEquals(NotificationStatus.PENDING, storedLog("c").status)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("d").status)
            assertEquals(2, balancesBefore.size)
            assertEquals(balancesBefore, repo.getAccounts().associate { it.id to it.currentBalance })
        }

    /** Account acc-1 of [PKG], a balance row at T0+[balanceAt] and an adjustment after it at T0+200. */
    private suspend fun insertAdjustedAccount(
        balanceAt: Long,
        withKey: Boolean,
    ) {
        dbRule.db.accountDao().insert(account(bankPackageName = PKG))
        if (withKey) insertKey("1234", "acc-1")
        val rows = dbRule.db.transactionDao()
        rows.insert(transaction(id = "t-bal", timestamp = T0 + balanceAt, postBalance = 900_000L))
        rows.insert(
            transaction(id = "t-adj", type = TransactionType.ADJUSTMENT, timestamp = T0 + 200L)
                .copy(adjustmentDirection = AdjustmentDirection.OUT),
        )
    }

    @Test
    fun `covered log is not booked`() =
        runTest {
            insertAdjustedAccount(balanceAt = 150L, withKey = true)
            val bank = log("b1", postTime = 100L)
            seed(bank)

            val error = bookInTx(book(bank, target = AccountTarget.Existing("acc-1")))

            assertEquals(SettlementError.AdjustedAfter, error)
            assertTrue(liveRows().none { it.notificationLogId == "b1" })
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(1_000_000L, onlyAccount().currentBalance)
            assertEquals(NotificationStatus.PENDING, storedLog("b1").status)
        }

    @Test
    fun `covered log rolls back the new identifier`() =
        runTest {
            // No key yet, one live account of the package and type: the mask 1234 attaches to it (N-05).
            insertAdjustedAccount(balanceAt = 150L, withKey = false)
            val bank = log("b1", postTime = 100L)
            seed(bank)

            assertEquals(SettlementError.AdjustedAfter, bookInTx(book(bank, target = AccountTarget.Existing("acc-1"))))

            assertTrue(repo.getIdentifiers().isEmpty())
            assertEquals(listOf("acc-1"), repo.getAccounts().map { it.id })
        }

    @Test
    fun `covered log is never paired`() =
        runTest {
            insertAdjustedAccount(balanceAt = 150L, withKey = true)
            dbRule.db.accountDao().insert(account(id = "acc-2", bankPackageName = MOMO))
            dbRule.db.transactionDao().insert(
                transaction(
                    id = "partner",
                    accountId = "acc-2",
                    type = TransactionType.INCOME,
                    timestamp = T0 + 100L + 5 * MINUTE,
                ).copy(source = TransactionSource.NOTIFICATION_PARSER),
            )
            val bank = log("b1", postTime = 100L, amount = 150_000L)
            seed(bank)

            assertEquals(SettlementError.AdjustedAfter, bookInTx(book(bank, target = AccountTarget.Existing("acc-1"))))

            assertTrue(liveRows().none { it.type == TransactionType.TRANSFER })
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("partner")!!
                    .deletedAt,
            )
        }

    @Test
    fun `uncovered log books`() =
        runTest {
            // The last balance row (t=50) is before the log (t=100), so the adjustment at t=200 does not include it.
            insertAdjustedAccount(balanceAt = 50L, withKey = true)
            val bank = log("b1", postTime = 100L)
            seed(bank)

            assertNull(bookInTx(book(bank, target = AccountTarget.Existing("acc-1"))))

            assertEquals("acc-1", accountOfTx("tx-1"))
            assertEquals(NotificationStatus.CONFIRMED, storedLog("b1").status)
        }

    private companion object {
        const val PKG = "com.test.bank"
        const val MOMO = "com.test.momo"
        const val MINUTE = 60_000L
        const val T0 = 1_000_000_000_000L
        val SETTLE_NOW: Long = Instant.parse("2026-11-03T00:00:00Z").toEpochMilli()
        const val NOW = T0 + 5_000L
    }
}
