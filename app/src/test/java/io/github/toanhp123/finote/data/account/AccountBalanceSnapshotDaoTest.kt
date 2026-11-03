package io.github.toanhp123.finote.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.data.snapshot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountBalanceSnapshotDaoTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val dao get() = dbRule.db.accountBalanceSnapshotDao()

    @Test
    fun `snapshot round-trips every column`() =
        runTest {
            dbRule.db.accountDao().insert(account())
            val snapshot = snapshot()
            dao.insert(snapshot)
            assertEquals(snapshot, dao.getById(snapshot.id))
        }

    @Test
    fun `update changes the stored row`() =
        runTest {
            dbRule.db.accountDao().insert(account())
            val original = snapshot()
            dao.insert(original)
            val changed = original.copy(closingBalance = 9L, updatedAt = 1_700_000_001_000L)
            dao.update(changed)
            assertEquals(changed, dao.getById(original.id))
        }

    @Test
    fun `duplicate account and month fails`() =
        runTest {
            dbRule.db.accountDao().insert(account())
            dao.insert(snapshot(id = "snap-1"))
            val message = runCatching { dao.insert(snapshot(id = "snap-2")) }.exceptionOrNull()?.message
            assertNotNull(message)
            assertTrue(message.orEmpty(), message.orEmpty().contains("UNIQUE constraint failed"))
        }

    @Test
    fun `snapshots of two accounts share a month`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-1"))
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(snapshot(id = "snap-1", accountId = "acc-1"))
            dao.insert(snapshot(id = "snap-2", accountId = "acc-2"))
            assertNotNull(dao.getById("snap-1"))
            assertNotNull(dao.getById("snap-2"))
        }

    @Test
    fun `hard-deleting an account removes its snapshots`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-1"))
            dao.insert(snapshot(id = "snap-1", accountId = "acc-1"))
            dbRule.execSql("DELETE FROM accounts WHERE id = 'acc-1'")
            assertNull(dao.getById("snap-1"))
        }

    @Test
    fun `observeByAccount orders by month`() =
        runTest {
            dbRule.db.accountDao().insert(account())
            dao.insert(snapshot(id = "snap-dec", monthYear = "2026-12"))
            dao.insert(snapshot(id = "snap-oct", monthYear = "2026-10"))
            assertEquals(listOf("2026-10", "2026-12"), dao.observeByAccount("acc-1").first().map { it.monthYear })
        }

    @Test
    fun `observeByAccount returns only that account`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-1"))
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(snapshot(id = "snap-1", accountId = "acc-1", monthYear = "2026-11"))
            dao.insert(snapshot(id = "snap-2", accountId = "acc-2", monthYear = "2026-11"))
            assertEquals(listOf("snap-1"), dao.observeByAccount("acc-1").first().map { it.id })
        }

    @Test
    fun `getByAccount returns that account ordered by month`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-1"))
            dbRule.db.accountDao().insert(account(id = "acc-2"))
            dao.insert(snapshot(id = "snap-dec", accountId = "acc-1", monthYear = "2026-12"))
            dao.insert(snapshot(id = "snap-oct", accountId = "acc-1", monthYear = "2026-10"))
            dao.insert(snapshot(id = "snap-other", accountId = "acc-2", monthYear = "2026-11"))
            assertEquals(listOf("2026-10", "2026-12"), dao.getByAccount("acc-1").map { it.monthYear })
        }
}
