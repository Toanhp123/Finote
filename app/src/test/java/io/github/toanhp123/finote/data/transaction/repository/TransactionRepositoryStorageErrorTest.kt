package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A SQLite failure inside a write reaches callers as a [StorageException]. */
@RunWith(AndroidJUnit4::class)
class TransactionRepositoryStorageErrorTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `write maps SQLException to StorageException`() =
        runTest {
            val repo = TransactionRepositoryImpl(dbRule.db, clock)
            dbRule.db.accountDao().insert(account(id = "acc-1", currentBalance = 1_000_000L))
            repo.add(domainTx())

            val failure = runCatching { repo.add(domainTx()) }.exceptionOrNull()

            assertTrue(failure.toString(), failure is StorageException)
            assertTrue(failure?.cause.toString(), failure?.cause is android.database.SQLException)
        }

    @Test
    fun `read maps SQLException to StorageException`() =
        runTest {
            val repo = TransactionRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE transactions")

            val failure = runCatching { repo.getTransaction("tx-1") }.exceptionOrNull()

            assertTrue(failure.toString(), failure is StorageException)
            assertTrue(failure?.cause.toString(), failure?.cause is android.database.SQLException)
        }
}
