package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.account.repository.AccountMergeRepositoryImpl
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.notification.mapper.toEntity
import io.github.toanhp123.finote.data.notification.repository.NotificationSettlementRepositoryImpl
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.service.balanceDriftKey
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParsedFields
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Unpair on a real database: the pair is built by the real settlement repository, as the worker would. */
@RunWith(AndroidJUnit4::class)
class TransferPairRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var settlement: NotificationSettlementRepositoryImpl
    private lateinit var repo: TransactionRepositoryImpl
    private lateinit var pairs: TransferPairRepositoryImpl

    @Before
    fun setUp() {
        settlement = NotificationSettlementRepositoryImpl(dbRule.db, clock)
        repo = TransactionRepositoryImpl(dbRule.db, clock)
        pairs = TransferPairRepositoryImpl(dbRule.db, clock)
    }

    private fun log(
        id: String,
        type: TransactionType,
        mask: String?,
        postBalance: Long,
        postTime: Long,
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
        status = NotificationStatus.PENDING,
        failureReason = null,
        matchedRuleId = "rule",
        parsed = ParsedFields(500_000L, type, mask, postBalance, "Parsed shop", null),
    )

    private fun debit() = log("d", TransactionType.EXPENSE, "1111", 1_500_000L, 1_000L)

    private fun credit(
        id: String = "c",
        postBalance: Long = 700_000L,
    ) = log(id, TransactionType.INCOME, "2222", postBalance, 1_000L + 5 * MINUTE)

    private suspend fun seed(vararg logs: NotificationLog) =
        logs.forEach { dbRule.db.notificationLogDao().insert(it.toEntity()) }

    private suspend fun book(
        bank: NotificationLog,
        transactionId: String,
        wallet: NotificationLog? = null,
    ) = settlement.book(
        SettlementDecision.Book(
            bank,
            wallet,
            AccountTarget.New(PKG, bank.parsed?.accountMask, "Vietcombank", AccountType.BANK, IdentifierKind.ACCOUNT),
            merchant = "Shop",
            note = null,
            rule = null,
        ),
        transactionId,
        NOW,
    )

    /** A 500_000 debit on account 1111, then a credit on 2222 five minutes later; returns the transfer id. */
    private suspend fun pair(): String {
        seed(debit(), credit())
        book(debit(), "tx-1")
        book(credit(), "tx-2")
        return liveRows().single().id
    }

    private suspend fun liveRows() =
        dbRule.db
            .transactionDao()
            .observeTransactions(null)
            .first()

    private suspend fun row(id: String) = checkNotNull(dbRule.db.transactionDao().getById(id))

    private suspend fun accountOf(mask: String) =
        settlement.getIdentifiers().single { it.mask == mask }.let { key ->
            settlement.getAccounts().single { it.id == key.accountId }
        }

    private suspend fun balanceOf(id: String) = checkNotNull(dbRule.db.accountDao().getById(id)).currentBalance

    private suspend fun storedLog(id: String) =
        dbRule.db
            .notificationLogDao()
            .getAll()
            .single { it.id == id }

    private suspend fun assertNoDrift(accountId: String) {
        val stored = checkNotNull(dbRule.db.accountDao().getById(accountId)).toDomain()
        val rows =
            dbRule.db
                .transactionDao()
                .getActiveByAccount(accountId)
                .map { it.toDomain() }
        assertNull(balanceDriftKey(stored, rows))
    }

    @Test
    fun `unpair restores both rows exactly`() =
        runTest {
            seed(debit(), credit())
            book(debit(), "tx-1")
            val firstBefore = row("tx-1")
            book(credit(), "tx-2")
            val transferId = liveRows().single().id
            val secondPaired = row("tx-2")

            pairs.unpair(transferId, UNPAIR_NOW)

            assertEquals(firstBefore.copy(updatedAt = UNPAIR_NOW), row("tx-1"))
            assertEquals(
                secondPaired.copy(deletedAt = null, pairedIntoId = null, updatedAt = UNPAIR_NOW),
                row("tx-2"),
            )
            assertEquals(UNPAIR_NOW, row(transferId).deletedAt)
            assertEquals(listOf("tx-1", "tx-2"), liveRows().map { it.id }.sorted())
            listOf("d" to "tx-1", "c" to "tx-2").forEach { (logId, txId) ->
                assertEquals(NotificationStatus.CONFIRMED, storedLog(logId).status)
                assertEquals(txId, storedLog(logId).linkedTransactionId)
            }
            // Same as two separate rows: a started at 2_000_000, b at 200_000.
            assertEquals(1_500_000L, balanceOf(accountOf("1111").id))
            assertEquals(700_000L, balanceOf(accountOf("2222").id))
            assertNoDrift(accountOf("1111").id)
            assertNoDrift(accountOf("2222").id)
        }

    @Test
    fun `unpair after the user moved T to a third and fourth account rebuilds all four`() =
        runTest {
            val transferId = pair()
            dbRule.db.accountDao().insert(account(id = "acc-x", currentBalance = 1_000_000L))
            dbRule.db.accountDao().insert(account(id = "acc-y", currentBalance = 1_000_000L))
            repo.update(row(transferId).toDomain().copy(accountId = "acc-x", toAccountId = "acc-y"))
            assertEquals(500_000L, balanceOf("acc-x"))
            assertEquals(1_500_000L, balanceOf("acc-y"))

            pairs.unpair(transferId, UNPAIR_NOW)

            assertEquals(1_000_000L, balanceOf("acc-x"))
            assertEquals(1_000_000L, balanceOf("acc-y"))
            assertEquals(1_500_000L, balanceOf(accountOf("1111").id))
            assertEquals(700_000L, balanceOf(accountOf("2222").id))
        }

    @Test
    fun `unpair after T was switched to EXPENSE works`() =
        runTest {
            val transferId = pair()
            repo.update(
                row(transferId).toDomain().copy(
                    type = TransactionType.EXPENSE,
                    toAccountId = null,
                    toPostBalance = null,
                    toTimestamp = null,
                ),
            )
            assertEquals(200_000L, balanceOf(accountOf("2222").id))

            pairs.unpair(transferId, UNPAIR_NOW)

            assertEquals(listOf("tx-1", "tx-2"), liveRows().map { it.id }.sorted())
            assertEquals(1_500_000L, balanceOf(accountOf("1111").id))
            assertEquals(700_000L, balanceOf(accountOf("2222").id))
        }

    @Test
    fun `restored rows never pair again`() =
        runTest {
            val transferId = pair()
            pairs.unpair(transferId, NOW) // the clock has not moved since the pair was made
            val again = credit(id = "c2", postBalance = 1_200_000L)
            seed(again)

            book(again, "tx-3")

            val rows = liveRows()
            assertEquals(listOf("tx-1", "tx-2", "tx-3"), rows.map { it.id }.sorted())
            assertFalse(rows.any { it.type == TransactionType.TRANSFER })
            assertNull(row("tx-3").pairedIntoId)
        }

    @Test
    fun `unpair after merging B into D`() =
        runTest {
            val transferId = pair()
            val b = accountOf("2222")
            dbRule.db.accountDao().insert(
                account(id = "acc-d", currentBalance = 200_000L, bankPackageName = PKG),
            )
            AccountMergeRepositoryImpl(dbRule.db, clock).merge(b.id, "acc-d", NOW)
            assertEquals("acc-d", row("tx-2").accountId)

            pairs.unpair(transferId, UNPAIR_NOW)

            assertEquals("acc-d", row("tx-2").accountId)
            assertNull(row("tx-2").deletedAt)
            assertEquals(700_000L, balanceOf("acc-d"))
            assertEquals(1_500_000L, balanceOf(accountOf("1111").id))
            assertNoDrift("acc-d")
        }

    @Test
    fun `moved notification rows still auto-pair after merge`() =
        runTest {
            seed(debit(), credit())
            book(debit(), "tx-1")
            dbRule.db.accountDao().insert(
                account(id = "acc-d", currentBalance = 2_000_000L, bankPackageName = PKG),
            )
            // A merge time after the booking time, so an updatedAt bump would show.
            AccountMergeRepositoryImpl(dbRule.db, clock).merge(accountOf("1111").id, "acc-d", NOW + 1)
            assertEquals("acc-d", row("tx-1").accountId)
            assertEquals(row("tx-1").createdAt, row("tx-1").updatedAt)

            book(credit(), "tx-2")

            val transfer = liveRows().single()
            assertEquals(TransactionType.TRANSFER, transfer.type)
            assertEquals("acc-d", transfer.accountId)
            assertEquals(accountOf("2222").id, transfer.toAccountId)
        }

    @Test
    fun `a wallet log deduplicated into a paired row stays untouched`() =
        runTest {
            val wallet = log("w", TransactionType.INCOME, null, 700_000L, 1_000L + 5 * MINUTE + 10_000L, MOMO)
            seed(debit(), credit(), wallet)
            book(debit(), "tx-1")
            book(credit(), "tx-2", wallet)
            val transferId = liveRows().single().id

            pairs.unpair(transferId, UNPAIR_NOW)

            assertEquals(NotificationStatus.DEDUPLICATED, storedLog("w").status)
            assertEquals("tx-2", storedLog("w").linkedTransactionId)
            assertEquals(NotificationStatus.CONFIRMED, storedLog("c").status)
        }

    @Test
    fun `plain delete of T keeps originals hidden`() =
        runTest {
            val transferId = pair()

            DeleteTransactionUseCase(repo, RoomTransactionRunner(dbRule.db), clock)(transferId)

            assertEquals(emptyList<String>(), liveRows().map { it.id })
            listOf("tx-1", "tx-2").forEach {
                assertNotNull(row(it).deletedAt)
                assertEquals(transferId, row(it).pairedIntoId)
            }
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("d").status)
            assertEquals(NotificationStatus.PAIRED_TRANSFER, storedLog("c").status)
        }

    @Test
    fun `observeAutoPairedIds emits the transfer after pairing and nothing after unpair`() =
        runTest {
            val transferId = pair()

            pairs.observeAutoPairedIds().test {
                assertEquals(setOf(transferId), awaitItem())
                pairs.unpair(transferId, UNPAIR_NOW)
                assertEquals(emptySet<String>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    private companion object {
        const val PKG = "com.test.bank"
        const val MOMO = "com.test.momo"
        const val MINUTE = 60_000L
        const val T0 = 1_000_000_000_000L
        const val NOW = T0 + 5_000L
        const val UNPAIR_NOW = NOW + 60_000L
    }
}
