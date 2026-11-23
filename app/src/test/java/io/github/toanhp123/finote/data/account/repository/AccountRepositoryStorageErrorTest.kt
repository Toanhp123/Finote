package io.github.toanhp123.finote.data.account.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.assertStorageFailure
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** A SQLite failure in any account call reaches callers as a StorageException. */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryStorageErrorTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `read maps SQLException to StorageException`() =
        runTest {
            val repo = AccountRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE accounts")

            assertStorageFailure { repo.getAccount("acc-1") }
        }

    @Test
    fun `write maps SQLException to StorageException`() =
        runTest {
            val repo = AccountRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE accounts")

            assertStorageFailure { repo.add(account().toDomain()) }
        }

    @Test
    fun `transaction write maps SQLException to StorageException`() =
        runTest {
            val repo = AccountRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE accounts")

            assertStorageFailure { repo.setArchived("acc-1", archived = true, updatedAt = 1L) }
        }

    @Test
    fun `observe maps SQLException to StorageException`() =
        runTest {
            val repo = AccountRepositoryImpl(dbRule.db, clock)
            dbRule.execSql("DROP TABLE accounts")

            assertStorageFailure { repo.observeAccounts().first() }
        }
}
