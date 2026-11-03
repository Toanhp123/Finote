package io.github.toanhp123.finote.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.data.transaction.TransactionRepositoryImpl
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.transaction.model.Transaction
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
class AccountRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    // Fixed clock: "this month" is 2026-11.
    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: AccountRepositoryImpl
    private lateinit var transactions: TransactionRepositoryImpl

    @Before
    fun setUp() {
        repo = AccountRepositoryImpl(dbRule.db, clock)
        transactions = TransactionRepositoryImpl(dbRule.db, clock)
    }

    private fun domainAccount(
        id: String = "acc-1",
        initialBalance: Long = 1_000_000L,
    ): Account = account(id = id, currentBalance = initialBalance).toDomain()

    private fun domainTx(
        id: String = "tx-1",
        type: TransactionType = TransactionType.EXPENSE,
        amount: Long = 200_000L,
        accountId: String = "acc-1",
        toAccountId: String? = null,
        at: String = "2026-09-10T00:00:00Z",
    ) = Transaction(
        id = id,
        amount = amount,
        feeAmount = 0L,
        type = type,
        adjustmentDirection = null,
        accountId = accountId,
        toAccountId = toAccountId,
        timestamp = Instant.parse(at).toEpochMilli(),
        deletedAt = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun income50kInOctober() =
        domainTx(id = "tx-2", type = TransactionType.INCOME, amount = 50_000L, at = "2026-10-05T00:00:00Z")

    private suspend fun rowOf(id: String) = dbRule.db.accountDao().getById(id)

    private suspend fun snapshotsOf(id: String) = dbRule.db.accountBalanceSnapshotDao().getByAccount(id)

    private suspend fun failureOf(block: suspend () -> Unit) = runCatching { block() }.exceptionOrNull()

    @Test
    fun `add then get returns the account with currentBalance equal to initialBalance`() =
        runTest {
            repo.add(domainAccount().copy(currentBalance = 5L))

            val stored = repo.getAccount("acc-1")
            assertEquals(domainAccount(), stored)
            assertEquals(1_000_000L, stored?.currentBalance)
            assertNull(repo.getAccount("ghost"))
            assertEquals(listOf("acc-1"), repo.observeAccounts().first().map { it.id })
        }

    @Test
    fun `changing initialBalance rebuilds balance and every snapshot`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx())
            transactions.add(income50kInOctober())

            repo.update(domainAccount(initialBalance = 2_000_000L))

            assertEquals(1_850_000L, rowOf("acc-1")?.currentBalance)
            assertEquals(
                listOf(
                    Triple("2026-09", 2_000_000L, 1_800_000L),
                    Triple("2026-10", 1_800_000L, 1_850_000L),
                    Triple("2026-11", 1_850_000L, 1_850_000L),
                ),
                snapshotsOf("acc-1").map { Triple(it.monthYear, it.openingBalance, it.closingBalance) },
            )
        }

    @Test
    fun `changing initialBalance without transactions sets the balance and writes no snapshot`() =
        runTest {
            repo.add(domainAccount())

            repo.update(domainAccount(initialBalance = 2_000_000L))

            assertEquals(2_000_000L, rowOf("acc-1")?.currentBalance)
            assertEquals(2_000_000L, rowOf("acc-1")?.initialBalance)
            assertTrue(snapshotsOf("acc-1").isEmpty())
        }

    @Test
    fun `update keeps currentBalance createdAt and deletedAt of the stored row`() =
        runTest {
            repo.add(domainAccount())
            val before = checkNotNull(rowOf("acc-1"))

            repo.update(
                domainAccount().copy(
                    name = "Renamed",
                    currentBalance = 1L,
                    createdAt = 99L,
                    updatedAt = 8L,
                    deletedAt = 7L,
                ),
            )

            val after = checkNotNull(rowOf("acc-1"))
            assertEquals("Renamed", after.name)
            assertEquals(8L, after.updatedAt)
            assertEquals(before.currentBalance, after.currentBalance)
            assertEquals(before.createdAt, after.createdAt)
            assertNull(after.deletedAt)
        }

    @Test
    fun `update of a soft-deleted account fails and changes nothing`() =
        runTest {
            repo.add(domainAccount())
            repo.softDelete("acc-1", deletedAt = 5L)
            val before = rowOf("acc-1")

            val failure = failureOf { repo.update(domainAccount(initialBalance = 2_000_000L).copy(name = "Renamed")) }

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(before, rowOf("acc-1"))
            assertTrue(failureOf { repo.update(domainAccount(id = "ghost")) } is IllegalArgumentException)
        }

    @Test
    fun `a stale update does not undo an archive`() =
        runTest {
            repo.add(domainAccount())
            repo.setArchived("acc-1", archived = true, updatedAt = 50L)

            repo.update(domainAccount().copy(name = "Renamed", isArchived = false))

            assertEquals("Renamed", rowOf("acc-1")?.name)
            assertEquals(true, rowOf("acc-1")?.isArchived)
        }

    @Test
    fun `setArchived flips the flag and leaves snapshots alone`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx())
            val snapshotsBefore = snapshotsOf("acc-1")

            repo.setArchived("acc-1", archived = true, updatedAt = 50L)
            assertEquals(true, rowOf("acc-1")?.isArchived)
            assertEquals(50L, rowOf("acc-1")?.updatedAt)
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))

            repo.setArchived("acc-1", archived = false, updatedAt = 60L)
            assertEquals(false, rowOf("acc-1")?.isArchived)
            assertEquals(60L, rowOf("acc-1")?.updatedAt)
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
        }

    @Test
    fun `setArchived on a missing or deleted account fails`() =
        runTest {
            repo.add(domainAccount())
            repo.softDelete("acc-1", deletedAt = 5L)
            val before = rowOf("acc-1")

            val onDeleted = failureOf { repo.setArchived("acc-1", archived = true, updatedAt = 50L) }
            val onMissing = failureOf { repo.setArchived("ghost", archived = true, updatedAt = 50L) }

            assertTrue(onDeleted.toString(), onDeleted is IllegalArgumentException)
            assertTrue(onMissing.toString(), onMissing is IllegalArgumentException)
            assertEquals(before, rowOf("acc-1"))
        }

    @Test
    fun `softDelete with a live source or transfer destination transaction fails and writes nothing`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            repo.add(domainAccount("acc-3"))
            transactions.add(domainTx(id = "tx-src", accountId = "acc-1"))
            transactions.add(
                domainTx(id = "tx-xfer", type = TransactionType.TRANSFER, accountId = "acc-3", toAccountId = "acc-2"),
            )
            val acc1 = rowOf("acc-1")
            val acc2 = rowOf("acc-2")

            val onSource = failureOf { repo.softDelete("acc-1", deletedAt = 5L) }
            val onDestination = failureOf { repo.softDelete("acc-2", deletedAt = 5L) }

            assertTrue(onSource.toString(), onSource is IllegalArgumentException)
            assertTrue(onDestination.toString(), onDestination is IllegalArgumentException)
            assertEquals(acc1, rowOf("acc-1"))
            assertEquals(acc2, rowOf("acc-2"))
        }

    @Test
    fun `softDelete succeeds when only deleted transactions remain, including a deleted transfer into it`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            transactions.add(domainTx(id = "tx-xfer", type = TransactionType.TRANSFER, toAccountId = "acc-2"))
            transactions.softDelete("tx-xfer", deletedAt = 4L)

            repo.softDelete("acc-2", deletedAt = 5L)
            repo.softDelete("acc-1", deletedAt = 6L)

            assertEquals(5L, rowOf("acc-2")?.deletedAt)
            assertEquals(5L, rowOf("acc-2")?.updatedAt)
            assertEquals(6L, rowOf("acc-1")?.deletedAt)
            assertNotNull(repo.getAccount("acc-1"))
            assertTrue(repo.observeAccounts().first().isEmpty())
        }

    @Test
    fun `softDelete twice is a no-op`() =
        runTest {
            repo.add(domainAccount())
            repo.softDelete("acc-1", deletedAt = 5L)
            repo.softDelete("acc-1", deletedAt = 9L)

            assertEquals(5L, rowOf("acc-1")?.deletedAt)
            assertEquals(5L, rowOf("acc-1")?.updatedAt)
            assertTrue(failureOf { repo.softDelete("ghost", deletedAt = 9L) } is IllegalArgumentException)
        }

    @Test
    fun `failed rebuild rolls back the account row`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx(type = TransactionType.INCOME, amount = 50_000L))
            val rowBefore = rowOf("acc-1")
            val snapshotsBefore = snapshotsOf("acc-1")

            val failure =
                failureOf { repo.update(domainAccount(initialBalance = Long.MAX_VALUE).copy(name = "Renamed")) }

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(rowBefore, rowOf("acc-1"))
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
        }
}
