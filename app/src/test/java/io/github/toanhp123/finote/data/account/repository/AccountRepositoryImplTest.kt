package io.github.toanhp123.finote.data.account.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.github.toanhp123.finote.data.account.mapper.toDomain
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.transaction.repository.TransactionRepositoryImpl
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.common.StorageException
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
    fun `update keeps type, currency, bank key and initial balance`() =
        runTest {
            dbRule.db.accountDao().insert(account(bankPackageName = "com.bank"))
            transactions.add(domainTx())
            val before = checkNotNull(rowOf("acc-1"))

            repo.update(
                checkNotNull(repo.getAccount("acc-1")).copy(
                    name = "Renamed",
                    color = 5,
                    type = AccountType.WALLET,
                    currency = "USD",
                    bankPackageName = "com.other",
                    initialBalance = 2_000_000L,
                ),
            )

            val after = checkNotNull(rowOf("acc-1"))
            assertEquals("Renamed", after.name)
            assertEquals(5, after.color)
            assertEquals(before.type, after.type)
            assertEquals(before.currency, after.currency)
            assertEquals(before.bankPackageName, after.bankPackageName)
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
    fun `update of a missing account is a silent no-op`() =
        runTest {
            repo.add(domainAccount())
            val before = rowOf("acc-1")

            repo.update(domainAccount(id = "ghost").copy(name = "Renamed"))

            assertEquals(before, rowOf("acc-1"))
            assertNull(rowOf("ghost"))
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
    fun `setArchived on a missing account is a silent no-op`() =
        runTest {
            repo.add(domainAccount())
            val before = rowOf("acc-1")

            repo.setArchived("ghost", archived = true, updatedAt = 50L)

            assertEquals(before, rowOf("acc-1"))
            assertNull(rowOf("ghost"))
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
        }

    @Test
    fun `softDelete of a missing account is a silent no-op`() =
        runTest {
            repo.softDelete("ghost", deletedAt = 9L)

            assertNull(rowOf("ghost"))
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
    fun `ignore a missing account is a silent no-op`() =
        runTest {
            repo.add(domainAccount())
            val before = rowOf("acc-1")

            repo.setIgnored("ghost", true, shouldTrashOldTransactions = true, now = 50L)

            assertEquals(before, rowOf("acc-1"))
            assertNull(rowOf("ghost"))
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

    private fun identifier(
        mask: String,
        accountId: String,
        kind: IdentifierKind = IdentifierKind.ACCOUNT,
    ) = AccountIdentifier(PKG, kind, mask, accountId)

    private suspend fun ownerOf(
        mask: String,
        kind: IdentifierKind = IdentifierKind.ACCOUNT,
    ) = dbRule.db
        .accountIdentifierDao()
        .get(PKG, kind, mask)
        ?.accountId

    @Test
    fun `setInitialBalance fixes the start and rebuilds the balance`() =
        runTest {
            dbRule.db.accountDao().insert(account(currentBalance = 0L).copy(isInitialized = false))
            transactions.add(
                domainTx(type = TransactionType.INCOME, amount = 100_000L, at = "2026-10-05T00:00:00Z"),
            )

            repo.setInitialBalance("acc-1", 500_000L, now = 50L)

            val row = checkNotNull(rowOf("acc-1"))
            assertEquals(500_000L, row.initialBalance)
            assertEquals(600_000L, row.currentBalance)
            assertTrue(row.isInitialized)
            val snapshots = snapshotsOf("acc-1").associateBy { it.monthYear }
            assertEquals(500_000L, snapshots["2026-10"]?.openingBalance)
            assertEquals(600_000L, snapshots["2026-11"]?.closingBalance)
        }

    @Test
    fun `setInitialBalance on a missing account is a silent no-op`() =
        runTest {
            repo.setInitialBalance("ghost", 500_000L, now = 50L)

            assertNull(rowOf("ghost"))
        }

    @Test
    fun `setPrimary leaves exactly one primary`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))

            repo.setPrimary("acc-1", now = 50L)
            repo.setPrimary("acc-2", now = 60L)

            assertEquals(false, rowOf("acc-1")?.isPrimary)
            assertEquals(true, rowOf("acc-2")?.isPrimary)
            assertEquals(60L, rowOf("acc-2")?.updatedAt)
        }

    @Test
    fun `clearPrimary clears only that account`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.setPrimary("acc-1", now = 50L)

            repo.clearPrimary("acc-2", now = 60L)
            assertEquals(true, rowOf("acc-1")?.isPrimary)

            repo.clearPrimary("acc-1", now = 70L)
            assertEquals(false, rowOf("acc-1")?.isPrimary)
        }

    @Test
    fun `archiving the primary clears the star`() =
        runTest {
            repo.add(domainAccount().copy(isPrimary = true))

            repo.setArchived("acc-1", archived = true, updatedAt = 50L)
            assertEquals(false, rowOf("acc-1")?.isPrimary)

            // Unarchiving does not give the star back.
            repo.setArchived("acc-1", archived = false, updatedAt = 60L)
            assertEquals(false, rowOf("acc-1")?.isPrimary)
        }

    @Test
    fun `ignoring the primary clears the star`() =
        runTest {
            repo.add(domainAccount().copy(isPrimary = true))

            repo.setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = false, now = 50L)

            assertEquals(false, rowOf("acc-1")?.isPrimary)
        }

    @Test
    fun `softDelete of the primary clears the star`() =
        runTest {
            repo.add(domainAccount().copy(isPrimary = true))

            repo.softDelete("acc-1", deletedAt = 50L)

            assertEquals(false, rowOf("acc-1")?.isPrimary)
        }

    @Test
    fun `archiving a non-primary leaves the primary alone`() =
        runTest {
            repo.add(domainAccount("acc-1").copy(isPrimary = true))
            repo.add(domainAccount("acc-2"))

            repo.setArchived("acc-2", archived = true, updatedAt = 50L)

            assertEquals(true, rowOf("acc-1")?.isPrimary)
        }

    @Test
    fun `reorder writes sortOrder by position and keeps unlisted ids`() =
        runTest {
            repo.add(domainAccount("acc-1").copy(sortOrder = 0))
            repo.add(domainAccount("acc-2").copy(sortOrder = 1))
            repo.add(domainAccount("acc-3").copy(sortOrder = 2))
            repo.add(domainAccount("acc-4").copy(sortOrder = 9, isArchived = true))

            repo.reorder(listOf("acc-3", "acc-1", "acc-2"), now = 50L)

            val orders = listOf("acc-1", "acc-2", "acc-3", "acc-4").map { rowOf(it)?.sortOrder }
            assertEquals(listOf(1, 2, 0, 9), orders)
            assertEquals(50L, rowOf("acc-3")?.updatedAt)
        }

    @Test
    fun `setBankPackage does not overwrite a stored package`() =
        runTest {
            repo.add(domainAccount())

            repo.setBankPackage("acc-1", "com.first", now = 50L)
            repo.setBankPackage("acc-1", "com.second", now = 60L)

            assertEquals("com.first", rowOf("acc-1")?.bankPackageName)
            assertEquals(50L, rowOf("acc-1")?.updatedAt)
        }

    @Test
    fun `addIdentifier stores a free key`() =
        runTest {
            repo.add(domainAccount())

            repo.addIdentifier(identifier("1234", "acc-1"))

            assertEquals(identifier("1234", "acc-1"), repo.getIdentifier(PKG, IdentifierKind.ACCOUNT, "1234"))
        }

    @Test
    fun `addIdentifier repoints a key whose owner is deleted`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            repo.addIdentifier(identifier("1234", "acc-2"))
            repo.softDelete("acc-2", deletedAt = 50L)

            repo.addIdentifier(identifier("1234", "acc-1"))

            assertEquals("acc-1", ownerOf("1234"))
        }

    @Test
    fun `addIdentifier on a key of a live account throws and changes nothing`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            repo.addIdentifier(identifier("1234", "acc-2"))

            val failure = runCatching { repo.addIdentifier(identifier("1234", "acc-1")) }.exceptionOrNull()

            assertTrue("$failure", failure is StorageException)
            assertEquals("acc-2", ownerOf("1234"))
        }

    @Test
    fun `getIdentifier is null for an unknown key`() =
        runTest {
            assertNull(repo.getIdentifier(PKG, IdentifierKind.CARD, "0000"))
        }

    @Test
    fun `removeIdentifier deletes only that key`() =
        runTest {
            repo.add(domainAccount())
            repo.addIdentifier(identifier("1234", "acc-1"))
            repo.addIdentifier(identifier("1234", "acc-1", IdentifierKind.CARD))
            repo.addIdentifier(identifier("5678", "acc-1"))

            repo.removeIdentifier(identifier("1234", "acc-1"))

            assertNull(ownerOf("1234"))
            assertEquals("acc-1", ownerOf("1234", IdentifierKind.CARD))
            assertEquals("acc-1", ownerOf("5678"))
        }

    @Test
    fun `observeIdentifiers emits the keys of one account`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))
            repo.addIdentifier(identifier("5555", "acc-2"))

            repo.observeIdentifiers("acc-1").test {
                assertEquals(emptyList<AccountIdentifier>(), awaitItem())
                repo.addIdentifier(identifier("1234", "acc-1"))
                assertEquals(listOf(identifier("1234", "acc-1")), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `observeIdentifiedAccountIds emits owners`() =
        runTest {
            repo.add(domainAccount("acc-1"))
            repo.add(domainAccount("acc-2"))

            repo.observeIdentifiedAccountIds().test {
                assertEquals(emptySet<String>(), awaitItem())
                repo.addIdentifier(identifier("1234", "acc-1"))
                assertEquals(setOf("acc-1"), awaitItem())
                repo.addIdentifier(identifier("5678", "acc-2"))
                assertEquals(setOf("acc-1", "acc-2"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `getNextSortOrder is zero on an empty table and max plus one otherwise`() =
        runTest {
            assertEquals(0, repo.getNextSortOrder())

            repo.add(domainAccount("acc-1").copy(sortOrder = 4))
            repo.add(domainAccount("acc-2").copy(sortOrder = 2))

            assertEquals(5, repo.getNextSortOrder())
        }

    private companion object {
        const val PKG = "com.vcb"
    }
}
