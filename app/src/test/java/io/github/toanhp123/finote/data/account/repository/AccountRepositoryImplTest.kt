package io.github.toanhp123.finote.data.account.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.transaction.repository.TransactionRepositoryImpl
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
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
    fun `add forces nameKey to null`() =
        runTest {
            repo.add(domainAccount().copy(nameKey = "sys_x"))
            val stored = repo.getAccount("acc-1")
            assertNotNull(stored)
            assertNull(stored?.nameKey)
        }

    @Test
    fun `update with same name keeps nameKey`() =
        runTest {
            dbRule.db.accountDao().insert(account(name = "Cash").copy(nameKey = "sys_cash"))
            repo.update(checkNotNull(repo.getAccount("acc-1")).copy(sortOrder = 5))
            val row = rowOf("acc-1")
            assertNotNull(row)
            assertEquals("sys_cash", row?.nameKey)
        }

    @Test
    fun `update with new name clears nameKey`() =
        runTest {
            dbRule.db.accountDao().insert(account(name = "Cash").copy(nameKey = "sys_cash"))
            repo.update(checkNotNull(repo.getAccount("acc-1")).copy(name = "Ví"))
            val row = rowOf("acc-1")
            assertNotNull(row)
            assertNull(row?.nameKey)
        }

    @Test
    fun `update keeps type, currency, bank key, mask and initial balance`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = "com.bank", accountNumberMask = "1234"))
            transactions.add(domainTx())
            val before = checkNotNull(rowOf("acc-1"))

            repo.update(
                checkNotNull(repo.getAccount("acc-1")).copy(
                    name = "Renamed",
                    color = 5,
                    type = AccountType.WALLET,
                    currency = "USD",
                    bankPackageName = "com.other",
                    accountNumberMask = "9999",
                    initialBalance = 2_000_000L,
                ),
            )

            val after = checkNotNull(rowOf("acc-1"))
            assertEquals("Renamed", after.name)
            assertEquals(5, after.color)
            assertEquals(before.type, after.type)
            assertEquals(before.currency, after.currency)
            assertEquals(before.bankPackageName, after.bankPackageName)
            assertEquals(before.accountNumberMask, after.accountNumberMask)
            assertEquals(before.initialBalance, after.initialBalance)
            assertEquals(before.currentBalance, after.currentBalance)
        }

    @Test
    fun `update keeps an uninitialized account uninitialized`() =
        runTest {
            dbRule.db.accountDao().insert(account().copy(isInitialized = false))

            repo.update(checkNotNull(repo.getAccount("acc-1")).copy(name = "Renamed", isInitialized = true))

            assertEquals(false, rowOf("acc-1")?.isInitialized)
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
    fun `ignore without trash keeps transactions and balance`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx())

            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = false, now = 50L)

            assertEquals(true, rowOf("acc-1")?.isIgnored)
            assertEquals(50L, rowOf("acc-1")?.updatedAt)
            assertEquals(800_000L, rowOf("acc-1")?.currentBalance)
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.deletedAt,
            )
            assertEquals(1, repo.getTrashableTransactionCount("acc-1"))
        }

    @Test
    fun `ignore with trash soft-deletes non-transfers and rebuilds`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx())
            transactions.add(income50kInOctober())

            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = true, now = 50L)

            assertEquals(
                50L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.deletedAt,
            )
            assertEquals(
                50L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-2")
                    ?.deletedAt,
            )
            assertEquals(1_000_000L, rowOf("acc-1")?.currentBalance)
            assertEquals(0, repo.getTrashableTransactionCount("acc-1"))
        }

    @Test
    fun `transfers are not trashed and the other account balance is unchanged`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            transactions.add(domainTx(id = "tx-out", type = TransactionType.TRANSFER, toAccountId = "acc-2"))
            transactions.add(
                domainTx(id = "tx-in", type = TransactionType.TRANSFER, accountId = "acc-2", toAccountId = "acc-1"),
            )
            transactions.add(domainTx(id = "tx-exp"))
            val other = rowOf("acc-2")?.currentBalance

            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = true, now = 50L)

            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-out")
                    ?.deletedAt,
            )
            assertNull(
                dbRule.db
                    .transactionDao()
                    .getById("tx-in")
                    ?.deletedAt,
            )
            assertEquals(
                50L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-exp")
                    ?.deletedAt,
            )
            assertEquals(other, rowOf("acc-2")?.currentBalance)
            assertEquals(1_000_000L, rowOf("acc-1")?.currentBalance)
        }

    @Test
    fun `follow again clears the flag, trashed rows stay deleted`() =
        runTest {
            repo.add(domainAccount())
            transactions.add(domainTx())
            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = true, now = 50L)

            repo.setIgnored("acc-1", ignored = false, shouldTrashOldTransactions = false, now = 60L)

            assertEquals(false, rowOf("acc-1")?.isIgnored)
            assertEquals(60L, rowOf("acc-1")?.updatedAt)
            assertEquals(
                50L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.deletedAt,
            )
        }

    @Test
    fun `ignore a missing or deleted account is rejected`() =
        runTest {
            repo.add(domainAccount())
            repo.softDelete("acc-1", deletedAt = 5L)
            val before = rowOf("acc-1")

            val onDeleted = failureOf { repo.setIgnored("acc-1", true, shouldTrashOldTransactions = true, now = 50L) }
            val onMissing = failureOf { repo.setIgnored("ghost", true, shouldTrashOldTransactions = false, now = 50L) }

            assertTrue(onDeleted.toString(), onDeleted is IllegalArgumentException)
            assertTrue(onMissing.toString(), onMissing is IllegalArgumentException)
            assertEquals(before, rowOf("acc-1"))
        }

    @Test
    fun `update keeps isIgnored and mergedIntoId`() =
        runTest {
            repo.add(domainAccount())
            dbRule.db.accountDao().update(checkNotNull(rowOf("acc-1")).copy(mergedIntoId = "acc-9"))
            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = false, now = 50L)

            repo.update(domainAccount().copy(name = "x", isIgnored = false, mergedIntoId = null))

            assertEquals("x", rowOf("acc-1")?.name)
            assertEquals(true, rowOf("acc-1")?.isIgnored)
            assertEquals("acc-9", rowOf("acc-1")?.mergedIntoId)
        }
}
