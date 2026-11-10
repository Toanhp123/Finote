package io.github.toanhp123.finote.data.transaction

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Ignored accounts are not usable: the write path rejects them like archived ones. */
@RunWith(AndroidJUnit4::class)
class TransactionRepositoryUsableAccountTest {
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

    private suspend fun ignore(accountId: String) {
        val dao = dbRule.db.accountDao()
        dao.update(checkNotNull(dao.getById(accountId)).copy(isIgnored = true))
    }

    private suspend fun balanceOf(accountId: String) =
        dbRule.db
            .accountDao()
            .getById(accountId)
            ?.currentBalance

    private suspend fun failureOf(block: suspend () -> Unit) = runCatching { block() }.exceptionOrNull()

    @Test
    fun `add on an ignored account is rejected`() =
        runTest {
            ignore("acc-1")
            val failure = failureOf { repo.add(domainTx()) }
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(1_000_000L, balanceOf("acc-1"))
        }

    @Test
    fun `update of a row on an ignored account is rejected`() =
        runTest {
            repo.add(domainTx())
            ignore("acc-1")

            val failure = failureOf { repo.update(domainTx(accountId = "acc-2")) }

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(
                "acc-1",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.accountId,
            )
            assertEquals(1_000_000L, balanceOf("acc-2"))
        }

    @Test
    fun `usable account ids skip ignored accounts`() =
        runTest {
            ignore("acc-2")
            assertEquals(setOf("acc-1"), repo.getUsableAccountIds(setOf("acc-1", "acc-2")))
        }
}
