package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class LedgerWriterColdStartTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var writer: LedgerWriter

    @Before
    fun setUp() {
        writer = LedgerWriter(dbRule.db, clock)
    }

    private fun minute(n: Long) = Instant.parse("2026-11-10T00:00:00Z").toEpochMilli() + n * 60_000L

    private fun kindOf(amount: Long) = if (amount > 0) TransactionType.INCOME else TransactionType.EXPENSE

    /** A parsed bank row at minute [at]: [amount] is signed, [post] is the bank balance printed after it. */
    private fun bank(
        id: String,
        amount: Long,
        at: Long,
        post: Long,
    ): Transaction =
        domainTx(id = id, type = kindOf(amount), amount = Math.abs(amount)).copy(
            timestamp = minute(at),
            createdAt = minute(at),
            source = TransactionSource.NOTIFICATION_PARSER,
            postBalance = post,
        )

    private suspend fun young() =
        dbRule.db.accountDao().insert(account(currentBalance = 5L).copy(isInitialized = false))

    private suspend fun acc() = checkNotNull(dbRule.db.accountDao().getById("acc-1"))

    private suspend fun book(vararg rows: Transaction) = rows.forEach { writer.insert(it, coldStart = true) }

    @Test
    fun `swapped pair heals to the real start and stays provisional`() =
        runTest {
            young()

            book(bank("b", 300, 1, 1_200))
            assertEquals(900L, acc().initialBalance)
            book(bank("a", -100, 2, 900))

            assertEquals(1_000L, acc().initialBalance)
            assertEquals(1_200L, acc().currentBalance)
            assertFalse(acc().isInitialized)
        }

    @Test
    fun `balance rows 30 minutes apart lock the start`() =
        runTest {
            young()

            book(bank("b", 300, 1, 1_200), bank("a", -100, 2, 900), bank("c", -50, 31, 1_150))

            assertEquals(1_000L, acc().initialBalance)
            assertTrue(acc().isInitialized)
        }

    @Test
    fun `late row after the lock leaves the start unchanged`() =
        runTest {
            young()
            book(bank("b", 300, 1, 1_200), bank("a", -100, 2, 900), bank("c", -50, 31, 1_150))

            book(bank("late", 50, 0, 850))

            assertEquals(1_000L, acc().initialBalance)
        }

    @Test
    fun `manual account is never changed`() =
        runTest {
            dbRule.db.accountDao().insert(account(currentBalance = 5L))

            book(bank("a", -100, 1, 900))

            assertEquals(5L, acc().initialBalance)
            assertTrue(acc().isInitialized)
        }
}
