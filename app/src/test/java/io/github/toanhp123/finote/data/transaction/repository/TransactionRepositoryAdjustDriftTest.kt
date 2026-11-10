package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.transaction.mapper.toDomain
import io.github.toanhp123.finote.domain.account.service.detectBalanceDrift
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryAdjustDriftTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() =
        runTest {
            repo = TransactionRepositoryImpl(dbRule.db, clock)
            dbRule.db.accountDao().insert(account(id = "acc-1", currentBalance = 1_000_000L))
            dbRule.db.accountDao().insert(account(id = "acc-2", currentBalance = 1_000_000L))
        }

    /** A parsed bank row: [balance] is what the bank printed after it, which the app's own sum may disagree with. */
    private fun bankRow(
        balance: Long,
        id: String = "tx-1",
        accountId: String = "acc-1",
    ) = domainTx(id = id, accountId = accountId)
        .copy(source = TransactionSource.NOTIFICATION_PARSER, postBalance = balance)

    private suspend fun balanceOf(accountId: String) =
        dbRule.db
            .accountDao()
            .getById(accountId)
            ?.currentBalance

    private suspend fun liveTxCount() =
        dbRule.db
            .transactionDao()
            .observeTransactions(null)
            .first()
            .size

    private suspend fun driftOf(accountId: String) =
        detectBalanceDrift(
            checkNotNull(dbRule.db.accountDao().getById(accountId)).toDomain(),
            dbRule.db
                .transactionDao()
                .getActiveByAccount(accountId)
                .map { it.toDomain() },
        )?.difference

    @Test
    fun `drift above the app balance writes adjustment in`() =
        runTest {
            repo.add(bankRow(balance = 900_000L)) // the app computes 800_000

            val tx = repo.adjustDrift("adj-1", "acc-1", clock.millis())

            assertEquals(TransactionType.ADJUSTMENT, tx?.type)
            assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
            assertEquals(100_000L, tx?.amount)
            assertEquals(clock.millis(), tx?.timestamp)
            assertEquals(tx, repo.getTransaction("adj-1"))
            assertEquals(900_000L, balanceOf("acc-1"))
            val nov =
                dbRule.db
                    .accountBalanceSnapshotDao()
                    .getByAccount("acc-1")
                    .last()
            assertEquals(
                Triple("2026-11", 800_000L, 900_000L),
                Triple(nov.monthYear, nov.openingBalance, nov.closingBalance),
            )
            assertEquals(100_000L, nov.totalIncome)
        }

    @Test
    fun `drift below the app balance writes adjustment out`() =
        runTest {
            repo.add(bankRow(balance = 700_000L))

            val tx = repo.adjustDrift("adj-1", "acc-1", clock.millis())

            assertEquals(AdjustmentDirection.OUT, tx?.adjustmentDirection)
            assertEquals(100_000L, tx?.amount)
            assertEquals(700_000L, balanceOf("acc-1"))
        }

    @Test
    fun `no drift writes nothing`() =
        runTest {
            repo.add(bankRow(balance = 800_000L))

            assertNull(repo.adjustDrift("adj-1", "acc-1", clock.millis()))

            assertEquals(1, liveTxCount())
            assertEquals(800_000L, balanceOf("acc-1"))
        }

    @Test
    fun `a second call after adjusting writes nothing`() =
        runTest {
            repo.add(bankRow(balance = 900_000L))

            assertNotNull(repo.adjustDrift("adj-1", "acc-1", clock.millis()))
            assertNull(repo.adjustDrift("adj-2", "acc-1", clock.millis()))

            assertEquals(2, liveTxCount())
            assertEquals(900_000L, balanceOf("acc-1"))
        }

    @Test
    fun `adjustment is stored as ADJUSTMENT and is not the last manual account`() =
        runTest {
            repo.add(domainTx(id = "tx-manual", accountId = "acc-2"))
            repo.add(bankRow(balance = 900_000L))

            val tx = repo.adjustDrift("adj-1", "acc-1", clock.millis())

            assertEquals(TransactionSource.ADJUSTMENT, tx?.source)
            assertEquals(TransactionSource.ADJUSTMENT, repo.getTransaction("adj-1")?.source)
            assertEquals("acc-2", repo.getLastManualAccountId())
        }

    @Test
    fun `a booking that lands after the drift was shown is not lost or double counted`() =
        runTest {
            repo.add(bankRow(balance = 900_000L)) // the app computes 800_000: drift 100_000
            assertEquals(100_000L, driftOf("acc-1"))
            // A later bank expense settles after the banner was shown: the balance moves, the drift does not.
            // The old absolute target (stale 800_000 + 100_000) would have booked +200_000 and erased this expense.
            repo.add(
                domainTx(id = "tx-late", amount = 100_000L, at = "2026-09-20T00:00:00Z")
                    .copy(source = TransactionSource.NOTIFICATION_PARSER, postBalance = 800_000L),
            )
            assertEquals(700_000L, balanceOf("acc-1"))
            assertEquals(100_000L, driftOf("acc-1"))

            val tx = repo.adjustDrift("adj-1", "acc-1", clock.millis())

            assertEquals(100_000L, tx?.amount)
            assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
            assertEquals(800_000L, balanceOf("acc-1"))
            assertNull(driftOf("acc-1"))
        }

    @Test
    fun `rejects archived, deleted and missing accounts`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "arch"))
            dbRule.db.accountDao().update(checkNotNull(dbRule.db.accountDao().getById("arch")).copy(isArchived = true))
            dbRule.db.accountDao().insert(account(id = "gone", deletedAt = 5L))

            listOf("arch", "gone", "missing").forEach { id ->
                val failure = runCatching { repo.adjustDrift("adj-$id", id, clock.millis()) }.exceptionOrNull()
                assertTrue(failure.toString(), failure is IllegalArgumentException)
                assertEquals("account does not exist, is deleted, archived or ignored", failure?.message)
            }
            assertEquals(0, liveTxCount())
        }

    @Test
    fun `overflow in subtraction writes nothing`() =
        runTest {
            repo.add(bankRow(balance = Long.MIN_VALUE)) // MIN - 800_000 overflows

            val failure = runCatching { repo.adjustDrift("adj-1", "acc-1", clock.millis()) }.exceptionOrNull()

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(800_000L, balanceOf("acc-1"))
            assertEquals(1, liveTxCount())
        }

    @Test
    fun `overflow in negation writes nothing`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "zero", currentBalance = 0L))
            repo.add(domainTx(id = "in", type = TransactionType.INCOME, amount = 100L, accountId = "zero"))
            repo.add(
                bankRow(balance = Long.MIN_VALUE, id = "out", accountId = "zero").copy(amount = 100L, createdAt = 2L),
            )
            assertEquals(0L, balanceOf("zero")) // the difference is exactly Long.MIN_VALUE

            val failure = runCatching { repo.adjustDrift("adj-1", "zero", clock.millis()) }.exceptionOrNull()

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(0L, balanceOf("zero"))
            assertEquals(2, liveTxCount())
        }
}
