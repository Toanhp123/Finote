package io.github.toanhp123.finote.data.transaction.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.local.entity.AccountBalanceSnapshotEntity
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.category
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import io.github.toanhp123.finote.data.transaction.mapper.toEntity
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.AdjustmentDirection
import io.github.toanhp123.finote.domain.transaction.model.Transaction
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
import java.time.ZoneId
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class TransactionRepositoryImplTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    // Fixed clock: "this month" is 2026-11, so every account's snapshots run through 2026-11.
    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: TransactionRepositoryImpl

    @Before
    fun setUp() =
        runTest {
            repo = TransactionRepositoryImpl(dbRule.db, clock)
            dbRule.db.accountDao().insert(account(id = "acc-1", currentBalance = 1_000_000L))
            dbRule.db.accountDao().insert(account(id = "acc-2", currentBalance = 1_000_000L))
        }

    private suspend fun balanceOf(accountId: String) =
        dbRule.db
            .accountDao()
            .getById(accountId)
            ?.currentBalance

    private suspend fun snapshotsOf(accountId: String) = dbRule.db.accountBalanceSnapshotDao().getByAccount(accountId)

    /** Month, opening, closing: enough to see how a change ripples through the chain. */
    private fun List<AccountBalanceSnapshotEntity>.chain() = map { it.figures() }

    private fun AccountBalanceSnapshotEntity.figures() = Triple(monthYear, openingBalance, closingBalance)

    private fun income50kInOctober() =
        domainTx(id = "tx-2", type = TransactionType.INCOME, amount = 50_000L, at = "2026-10-05T00:00:00Z")

    @Test
    fun `observeTransactions maps rows and filters by account`() =
        runTest {
            repo.add(domainTx(id = "tx-exp"))
            repo.add(domainTx(id = "tx-trf", type = TransactionType.TRANSFER, toAccountId = "acc-2"))

            val all = repo.observeTransactions(null).first()
            assertEquals(setOf("tx-exp", "tx-trf"), all.map { it.id }.toSet())
            assertEquals(listOf("tx-trf"), repo.observeTransactions("acc-2").first().map { it.id })
        }

    @Test
    fun `add sets balance and snapshots`() =
        runTest {
            repo.add(domainTx())

            assertEquals(800_000L, balanceOf("acc-1"))
            assertEquals(
                clock.millis(),
                dbRule.db
                    .accountDao()
                    .getById("acc-1")
                    ?.updatedAt,
            )
            val snapshots = snapshotsOf("acc-1")
            assertEquals(
                listOf(
                    Triple("2026-09", 1_000_000L, 800_000L),
                    Triple("2026-10", 800_000L, 800_000L),
                    Triple("2026-11", 800_000L, 800_000L),
                ),
                snapshots.chain(),
            )
            assertEquals(200_000L, snapshots.first().totalExpense)
            assertEquals(1_000_000L, balanceOf("acc-2"))
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("acc-2"))
        }

    @Test
    fun `update amount ripples through later months`() =
        runTest {
            repo.add(domainTx())
            repo.update(domainTx(amount = 300_000L))

            assertEquals(700_000L, balanceOf("acc-1"))
            assertEquals(
                listOf(
                    Triple("2026-09", 1_000_000L, 700_000L),
                    Triple("2026-10", 700_000L, 700_000L),
                    Triple("2026-11", 700_000L, 700_000L),
                ),
                snapshotsOf("acc-1").chain(),
            )
        }

    @Test
    fun `moving the timestamp back changes the old month and every month after`() =
        runTest {
            repo.add(domainTx())
            repo.add(income50kInOctober())
            repo.update(domainTx(at = "2026-07-10T00:00:00Z"))

            assertEquals(850_000L, balanceOf("acc-1"))
            val snapshots = snapshotsOf("acc-1")
            assertEquals(
                listOf(
                    Triple("2026-07", 1_000_000L, 800_000L),
                    Triple("2026-08", 800_000L, 800_000L),
                    Triple("2026-09", 800_000L, 800_000L),
                    Triple("2026-10", 800_000L, 850_000L),
                    Triple("2026-11", 850_000L, 850_000L),
                ),
                snapshots.chain(),
            )
            assertEquals(0L, snapshots.single { it.monthYear == "2026-09" }.totalExpense)
        }

    @Test
    fun `moving the timestamp forward flattens the months it leaves`() =
        runTest {
            repo.add(domainTx(at = "2026-07-10T00:00:00Z"))
            val julyId = snapshotsOf("acc-1").first().id
            repo.update(domainTx(at = "2026-09-10T00:00:00Z"))

            val snapshots = snapshotsOf("acc-1")
            assertEquals(listOf("2026-07", "2026-08", "2026-09", "2026-10", "2026-11"), snapshots.map { it.monthYear })
            assertEquals(Triple("2026-07", 1_000_000L, 1_000_000L), snapshots.first().figures())
            assertEquals(0L, snapshots.first().totalExpense)
            assertEquals(julyId, snapshots.first().id)
            assertEquals(Triple("2026-09", 1_000_000L, 800_000L), snapshots[2].figures())
        }

    @Test
    fun `changing the account moves the effect to the new account`() =
        runTest {
            repo.add(domainTx())
            repo.update(domainTx(accountId = "acc-2"))

            assertEquals(1_000_000L, balanceOf("acc-1"))
            assertEquals(800_000L, balanceOf("acc-2"))
            val flat = snapshotsOf("acc-1").flatMap { listOf(it.openingBalance, it.closingBalance) }
            assertEquals(setOf(1_000_000L), flat.toSet())
            assertEquals(800_000L, snapshotsOf("acc-2").first().closingBalance)
        }

    @Test
    fun `transfer updates source and destination`() =
        runTest {
            repo.add(
                domainTx(
                    type = TransactionType.TRANSFER,
                    amount = 300_000L,
                    toAccountId = "acc-2",
                    at = "2026-11-02T00:00:00Z",
                ).copy(feeAmount = 1_000L),
            )

            assertEquals(699_000L, balanceOf("acc-1"))
            assertEquals(1_300_000L, balanceOf("acc-2"))
            val source = snapshotsOf("acc-1").single()
            assertEquals(300_000L, source.totalTransferOut)
            assertEquals(1_000L, source.totalExpense)
            val destination = snapshotsOf("acc-2").single()
            assertEquals(300_000L, destination.totalTransferIn)
            assertEquals(1_300_000L, destination.closingBalance)
        }

    @Test
    fun `soft delete stops counting the transaction but keeps the row`() =
        runTest {
            repo.add(domainTx())
            repo.softDelete("tx-1", deletedAt = 5_000L)

            assertEquals(1_000_000L, balanceOf("acc-1"))
            assertEquals(
                5_000L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.deletedAt,
            )
            assertEquals(emptyList<TransactionEntity>(), dbRule.db.transactionDao().getActiveByAccount("acc-1"))
        }

    @Test
    fun `deleting the first transaction flattens its month without removing the row`() =
        runTest {
            repo.add(domainTx())
            repo.add(income50kInOctober())
            val before = snapshotsOf("acc-1")
            repo.softDelete("tx-1", deletedAt = 5_000L)

            val after = snapshotsOf("acc-1")
            assertEquals(before.map { it.id }, after.map { it.id })
            assertEquals(
                listOf(
                    Triple("2026-09", 1_000_000L, 1_000_000L),
                    Triple("2026-10", 1_000_000L, 1_050_000L),
                    Triple("2026-11", 1_050_000L, 1_050_000L),
                ),
                after.chain(),
            )
            assertEquals(0L, after.first().totalExpense)
        }

    @Test
    fun `overflow rolls back the transaction, balance and snapshots`() =
        runTest {
            repo.add(domainTx(type = TransactionType.INCOME, amount = 100_000L))
            val snapshotsBefore = snapshotsOf("acc-1")
            val balanceBefore = balanceOf("acc-1")

            val failure =
                runCatching {
                    repo.add(domainTx(id = "tx-big", type = TransactionType.INCOME, amount = Long.MAX_VALUE))
                }.exceptionOrNull()
            assertTrue(failure.toString(), failure is ArithmeticException)

            assertNull(dbRule.db.transactionDao().getById("tx-big"))
            assertNotNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(balanceBefore, balanceOf("acc-1"))
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
        }

    @Test
    fun `a far future stored snapshot no longer blocks writes`() =
        runTest {
            dbRule.db.accountBalanceSnapshotDao().insert(
                AccountBalanceSnapshotEntity(
                    id = "snap-far",
                    accountId = "acc-1",
                    monthYear = "9999-01",
                    openingBalance = 1L,
                    closingBalance = 1L,
                    totalIncome = 0L,
                    totalExpense = 0L,
                    totalTransferIn = 0L,
                    totalTransferOut = 0L,
                    updatedAt = 1L,
                ),
            )
            repo.add(domainTx()) // 200k expense in 2026-09

            val far = snapshotsOf("acc-1").single { it.monthYear == "9999-01" }
            assertEquals(Triple("9999-01", 800_000L, 800_000L), far.figures())
            assertEquals(
                listOf(0L, 0L, 0L, 0L),
                listOf(far.totalIncome, far.totalExpense, far.totalTransferIn, far.totalTransferOut),
            )
            assertEquals("snap-far", far.id)
        }

    @Test
    fun `a corrupt live transaction can still be deleted`() =
        runTest {
            dbRule.db.transactionDao().insert(domainTx(id = "tx-old", at = "1900-01-10T00:00:00Z").toEntity())

            val failure = failureOf { repo.add(domainTx(id = "tx-new")) }
            assertTrue(failure.toString(), failure is IllegalArgumentException)

            repo.softDelete("tx-old", deletedAt = 2L)
            assertEquals(1_000_000L, balanceOf("acc-1"))
        }

    private suspend fun failureOf(block: suspend () -> Unit) = runCatching { block() }.exceptionOrNull()

    private fun transfer1to2(at: String = "2026-11-02T00:00:00Z") =
        domainTx(type = TransactionType.TRANSFER, amount = 300_000L, toAccountId = "acc-2", at = at)

    private suspend fun assertFlat(
        accountId: String,
        balance: Long = 1_000_000L,
    ) {
        assertEquals(balance, balanceOf(accountId))
        snapshotsOf(accountId).forEach { assertEquals(Triple(it.monthYear, balance, balance), it.figures()) }
    }

    @Test
    fun `update of a deleted transaction throws and changes nothing`() =
        runTest {
            repo.add(domainTx())
            repo.softDelete("tx-1", deletedAt = 5_000L)
            val snapshotsBefore = snapshotsOf("acc-1")

            val failure = failureOf { repo.update(domainTx(amount = 900_000L)) }

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            val row = dbRule.db.transactionDao().getById("tx-1")
            assertEquals(5_000L, row?.deletedAt)
            assertEquals(200_000L, row?.amount)
            assertEquals(1_000_000L, balanceOf("acc-1"))
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
        }

    @Test
    fun `soft delete twice keeps the first deletedAt`() =
        runTest {
            repo.add(domainTx())
            repo.softDelete("tx-1", deletedAt = 5_000L)
            repo.softDelete("tx-1", deletedAt = 9_000L)

            assertEquals(
                5_000L,
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.deletedAt,
            )
        }

    @Test
    fun `add to a deleted account throws and rolls back`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-gone", deletedAt = 1L))

            val onSource = failureOf { repo.add(domainTx(id = "tx-a", accountId = "acc-gone")) }
            val onDestination =
                failureOf {
                    repo.add(transfer1to2().copy(id = "tx-b", toAccountId = "acc-gone"))
                }

            assertTrue(onSource.toString(), onSource is IllegalArgumentException)
            assertTrue(onDestination.toString(), onDestination is IllegalArgumentException)
            assertNull(dbRule.db.transactionDao().getById("tx-a"))
            assertNull(dbRule.db.transactionDao().getById("tx-b"))
            assertFlat("acc-1")
            assertFlat("acc-gone")
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("acc-1"))
        }

    @Test
    fun `update to a deleted account throws and rolls back`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-gone", deletedAt = 1L))
            repo.add(domainTx())
            val snapshotsBefore = snapshotsOf("acc-1")

            val failure = failureOf { repo.update(domainTx(accountId = "acc-gone")) }

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(
                "acc-1",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.accountId,
            )
            assertEquals(800_000L, balanceOf("acc-1"))
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
            assertFlat("acc-gone")
        }

    @Test
    fun `changing the transfer destination restores the old one and credits the new one`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-3", currentBalance = 1_000_000L))
            repo.add(transfer1to2())
            repo.update(transfer1to2().copy(toAccountId = "acc-3"))

            assertEquals(700_000L, balanceOf("acc-1"))
            assertFlat("acc-2")
            assertEquals(1_300_000L, balanceOf("acc-3"))
            val credited = snapshotsOf("acc-3").single()
            assertEquals(Triple("2026-11", 1_000_000L, 1_300_000L), credited.figures())
            assertEquals(300_000L, credited.totalTransferIn)
        }

    @Test
    fun `soft delete of a transfer restores both accounts`() =
        runTest {
            repo.add(transfer1to2())
            repo.softDelete("tx-1", deletedAt = 5_000L)

            assertFlat("acc-1")
            assertFlat("acc-2")
            assertEquals(1, snapshotsOf("acc-1").size)
            assertEquals(1, snapshotsOf("acc-2").size)
        }

    @Test
    fun `overflow on the destination rolls back the source too`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-big", currentBalance = Long.MAX_VALUE - 100L))
            repo.add(domainTx(id = "tx-0", at = "2026-10-05T00:00:00Z"))
            val snapshotsBefore = snapshotsOf("acc-1")
            val balanceBefore = balanceOf("acc-1")

            val failure = failureOf { repo.add(transfer1to2().copy(toAccountId = "acc-big")) }

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertEquals(balanceBefore, balanceOf("acc-1"))
            assertEquals(snapshotsBefore, snapshotsOf("acc-1"))
            assertFlat("acc-big", Long.MAX_VALUE - 100L)
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("acc-big"))
        }

    @Test
    fun `deleting a transaction months ahead keeps the future rows flat and chained`() =
        runTest {
            repo.add(domainTx())
            repo.add(
                domainTx(id = "tx-f", type = TransactionType.INCOME, amount = 50_000L, at = "2027-02-10T00:00:00Z"),
            )
            val idsBefore = snapshotsOf("acc-1").map { it.id }
            repo.softDelete("tx-f", deletedAt = 5_000L)

            val snapshots = snapshotsOf("acc-1")
            assertEquals(idsBefore, snapshots.map { it.id })
            assertEquals(
                listOf("2026-09", "2026-10", "2026-11", "2026-12", "2027-01", "2027-02"),
                snapshots.map { it.monthYear },
            )
            assertEquals(800_000L, balanceOf("acc-1"))
            snapshots.zipWithNext().forEach { (a, b) -> assertEquals(a.closingBalance, b.openingBalance) }
            assertEquals(Triple("2027-02", 800_000L, 800_000L), snapshots.last().figures())
            assertEquals(0L, snapshots.last().totalIncome)
        }

    @Test
    fun `months follow the clock zone, not UTC`() =
        runTest {
            val hcm = ZoneId.of("Asia/Ho_Chi_Minh")
            val zoned = TransactionRepositoryImpl(dbRule.db, Clock.fixed(clock.instant(), hcm))
            // 20:00 UTC on Sep 30 is 03:00 on Oct 1 in Ho Chi Minh City.
            zoned.add(domainTx(at = "2026-09-30T20:00:00Z"))

            assertEquals(
                listOf(
                    Triple("2026-10", 1_000_000L, 800_000L),
                    Triple("2026-11", 800_000L, 800_000L),
                ),
                snapshotsOf("acc-1").chain(),
            )
        }

    private suspend fun archive(accountId: String) {
        val dao = dbRule.db.accountDao()
        dao.update(checkNotNull(dao.getById(accountId)).copy(isArchived = true))
    }

    @Test
    fun `adding to an archived account fails`() =
        runTest {
            archive("acc-1")
            val failure = failureOf { repo.add(domainTx()) }
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals("account does not exist, is deleted, archived or ignored", failure?.message)
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertFlat("acc-1")
        }

    @Test
    fun `a transfer into an archived account fails`() =
        runTest {
            archive("acc-2")
            val failure = failureOf { repo.add(transfer1to2()) }
            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertNull(dbRule.db.transactionDao().getById("tx-1"))
            assertFlat("acc-1")
            assertFlat("acc-2")
        }

    @Test
    fun `moving a transaction out of an archived account fails`() =
        runTest {
            repo.add(domainTx())
            archive("acc-1")

            val failure = failureOf { repo.update(domainTx(accountId = "acc-2")) }

            assertTrue(failure.toString(), failure is IllegalArgumentException)
            assertEquals(
                "acc-1",
                dbRule.db
                    .transactionDao()
                    .getById("tx-1")
                    ?.accountId,
            )
            assertEquals(800_000L, balanceOf("acc-1"))
            assertFlat("acc-2")
        }

    @Test
    fun `deleting a transaction of an archived account works`() =
        runTest {
            repo.add(domainTx())
            archive("acc-1")
            repo.softDelete("tx-1", deletedAt = 2L)
            assertEquals(1_000_000L, balanceOf("acc-1"))
        }

    @Test
    fun `usable account ids skip archived accounts`() =
        runTest {
            archive("acc-2")
            assertEquals(setOf("acc-1"), repo.getUsableAccountIds(setOf("acc-1", "acc-2")))
        }

    @Test
    fun `usable account ids skip deleted and unknown accounts`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "acc-gone", deletedAt = 1L))
            val active = repo.getUsableAccountIds(setOf("acc-1", "acc-gone", "ghost"))
            assertEquals(setOf("acc-1"), active)
        }

    @Test
    fun `category type is read and unknown is null`() =
        runTest {
            dbRule.db.categoryDao().insert(category(id = "cat-1"))
            assertEquals(CategoryType.EXPENSE, repo.getCategoryType("cat-1"))
            assertNull(repo.getCategoryType("ghost"))
        }

    private suspend fun liveTxCount() =
        dbRule.db
            .transactionDao()
            .observeTransactions(null)
            .first()
            .size

    @Test
    fun `reconcile above balance writes adjustment in`() =
        runTest {
            val tx = repo.reconcile("adj-1", "acc-1", 1_300_000L, clock.millis())

            assertEquals(TransactionType.ADJUSTMENT, tx?.type)
            assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
            assertEquals(300_000L, tx?.amount)
            assertEquals(0L, tx?.feeAmount)
            assertNull(tx?.categoryId)
            assertEquals(TransactionSource.ADJUSTMENT, tx?.source)
            assertEquals(clock.millis(), tx?.timestamp)
            assertEquals(clock.millis(), tx?.createdAt)
            assertEquals(tx, repo.getTransaction("adj-1"))
            assertEquals(1_300_000L, balanceOf("acc-1"))
            val nov = snapshotsOf("acc-1").single()
            assertEquals(Triple("2026-11", 1_000_000L, 1_300_000L), nov.figures())
            assertEquals(300_000L, nov.totalIncome)
        }

    @Test
    fun `reconcile below balance writes adjustment out`() =
        runTest {
            val tx = repo.reconcile("adj-1", "acc-1", 750_000L, clock.millis())

            assertEquals(AdjustmentDirection.OUT, tx?.adjustmentDirection)
            assertEquals(250_000L, tx?.amount)
            assertEquals(750_000L, balanceOf("acc-1"))
            val nov = snapshotsOf("acc-1").single()
            assertEquals(Triple("2026-11", 1_000_000L, 750_000L), nov.figures())
            assertEquals(250_000L, nov.totalExpense)
        }

    @Test
    fun `reconcile to zero takes the whole balance out`() =
        runTest {
            val tx = repo.reconcile("adj-1", "acc-1", 0L, clock.millis())

            assertEquals(AdjustmentDirection.OUT, tx?.adjustmentDirection)
            assertEquals(1_000_000L, tx?.amount)
            assertEquals(0L, balanceOf("acc-1"))
        }

    @Test
    fun `reconcile negative credit balance`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "card", currentBalance = -500_000L))

            val tx = repo.reconcile("adj-1", "card", -700_000L, clock.millis())

            assertEquals(AdjustmentDirection.OUT, tx?.adjustmentDirection)
            assertEquals(200_000L, tx?.amount)
            assertEquals(-700_000L, balanceOf("card"))
        }

    @Test
    fun `reconcile equal balance writes nothing`() =
        runTest {
            assertNull(repo.reconcile("adj-1", "acc-1", 1_000_000L, clock.millis()))

            assertEquals(0, liveTxCount())
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("acc-1"))
            assertEquals(1_000_000L, balanceOf("acc-1"))
        }

    @Test
    fun `reconcile twice with the same figure writes once`() =
        runTest {
            assertNotNull(repo.reconcile("adj-1", "acc-1", 1_300_000L, clock.millis()))
            assertNull(repo.reconcile("adj-2", "acc-1", 1_300_000L, clock.millis()))

            assertEquals(1, liveTxCount())
            assertNull(repo.getTransaction("adj-2"))
            assertEquals(1_300_000L, balanceOf("acc-1"))
        }

    @Test
    fun `reconcile only changes the current month snapshot`() =
        runTest {
            repo.add(domainTx())
            repo.reconcile("adj-1", "acc-1", 900_000L, clock.millis())

            assertEquals(
                listOf(
                    Triple("2026-09", 1_000_000L, 800_000L),
                    Triple("2026-10", 800_000L, 800_000L),
                    Triple("2026-11", 800_000L, 900_000L),
                ),
                snapshotsOf("acc-1").chain(),
            )
        }

    @Test
    fun `reconcile counts future dated transactions in the current balance`() =
        runTest {
            repo.add(domainTx(at = "2027-01-10T00:00:00Z"))
            assertEquals(800_000L, balanceOf("acc-1"))

            val tx = repo.reconcile("adj-1", "acc-1", 1_000_000L, clock.millis())

            assertEquals(AdjustmentDirection.IN, tx?.adjustmentDirection)
            assertEquals(200_000L, tx?.amount)
            assertEquals(1_000_000L, balanceOf("acc-1"))
        }

    @Test
    fun `reconcile rejects archived, deleted and missing accounts`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "arch"))
            archive("arch")
            dbRule.db.accountDao().insert(account(id = "gone", deletedAt = 5L))

            listOf("arch", "gone", "missing").forEach { id ->
                val failure = failureOf { repo.reconcile("adj-$id", id, 1L, clock.millis()) }
                assertTrue(failure.toString(), failure is IllegalArgumentException)
                assertEquals("account does not exist, is deleted, archived or ignored", failure?.message)
            }
            assertEquals(0, liveTxCount())
            listOf("arch", "gone").forEach { id ->
                assertEquals(1_000_000L, balanceOf(id))
                assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf(id))
            }
        }

    @Test
    fun `reconcile overflow in subtraction writes nothing`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "one", currentBalance = 1L))

            val failure = failureOf { repo.reconcile("adj-1", "one", Long.MIN_VALUE, clock.millis()) }

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(1L, balanceOf("one"))
            assertEquals(0, liveTxCount())
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("one"))
        }

    @Test
    fun `reconcile overflow in negation writes nothing`() =
        runTest {
            dbRule.db.accountDao().insert(account(id = "zero", currentBalance = 0L))

            val failure = failureOf { repo.reconcile("adj-1", "zero", Long.MIN_VALUE, clock.millis()) }

            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(0L, balanceOf("zero"))
            assertEquals(0, liveTxCount())
            assertEquals(emptyList<AccountBalanceSnapshotEntity>(), snapshotsOf("zero"))
        }

    @Test
    fun `soft deleting the adjustment restores the old balance`() =
        runTest {
            repo.reconcile("adj-1", "acc-1", 1_300_000L, clock.millis())
            repo.softDelete("adj-1", clock.millis())

            assertEquals(1_000_000L, balanceOf("acc-1"))
        }
}
