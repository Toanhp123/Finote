package io.github.toanhp123.finote.data.account

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.account
import io.github.toanhp123.finote.data.snapshot
import io.github.toanhp123.finote.data.transaction
import io.github.toanhp123.finote.data.transaction.TransactionEntity
import io.github.toanhp123.finote.data.transaction.TransactionRepositoryImpl
import io.github.toanhp123.finote.data.transaction.toDomain
import io.github.toanhp123.finote.domain.account.detectBalanceDrift
import io.github.toanhp123.finote.domain.account.model.AccountMergeRejectedException
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.MergeRejection
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
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
class AccountMergeTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    // Fixed clock: "this month" is 2026-11.
    private val clock = Clock.fixed(Instant.parse("2026-11-15T00:00:00Z"), ZoneOffset.UTC)
    private lateinit var repo: AccountRepositoryImpl

    @Before
    fun setUp() {
        repo = AccountRepositoryImpl(dbRule.db, clock)
    }

    private fun at(day: String) = Instant.parse("2026-$day").toEpochMilli()

    private suspend fun bank(
        id: String,
        balance: Long = 0L,
        type: AccountType = AccountType.BANK,
        pkg: String? = PKG,
        initialized: Boolean = true,
        deletedAt: Long? = null,
    ): AccountEntity {
        val row =
            account(
                id = id,
                type = type,
                currentBalance = balance,
                bankPackageName = pkg,
                accountNumberMask = id.takeLast(4),
                deletedAt = deletedAt,
            ).copy(isInitialized = initialized)
        dbRule.db.accountDao().insert(row)
        return row
    }

    // why: one named default per transaction field the merge tests vary.
    @Suppress("LongParameterList")
    private suspend fun tx(
        id: String,
        accountId: String,
        day: String,
        amount: Long = 100_000L,
        type: TransactionType = TransactionType.EXPENSE,
        toAccountId: String? = null,
        postBalance: Long? = null,
        deletedAt: Long? = null,
    ): TransactionEntity {
        val row =
            transaction(
                id = id,
                accountId = accountId,
                toAccountId = toAccountId,
                type = type,
                timestamp = at(day),
                postBalance = postBalance,
            ).copy(amount = amount, feeAmount = 0L, deletedAt = deletedAt)
        dbRule.db.transactionDao().insert(row)
        return row
    }

    private suspend fun rowOf(id: String) = checkNotNull(dbRule.db.accountDao().getById(id))

    private suspend fun txOf(id: String) = checkNotNull(dbRule.db.transactionDao().getById(id))

    private suspend fun snapshotMonths(id: String) =
        dbRule.db
            .accountBalanceSnapshotDao()
            .getByAccount(id)
            .map { it.monthYear }

    private suspend fun assertRejected(
        reason: MergeRejection,
        sourceId: String,
        targetId: String,
        txIds: List<String> = emptyList(),
    ) {
        suspend fun state() =
            listOf(sourceId, targetId).map { dbRule.db.accountDao().getById(it) } + txIds.map { txOf(it) }
        val before = state()

        val failure = runCatching { repo.merge(sourceId, targetId, NOW) }.exceptionOrNull()

        assertTrue(failure.toString(), failure is AccountMergeRejectedException)
        assertEquals(reason, (failure as AccountMergeRejectedException).reason)
        assertEquals(before, state())
    }

    @Test
    fun `merge moves source and destination columns, deleted rows too`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            bank("acc-c", pkg = "other")
            tx("live", "acc-a", "11-01T00:00:00Z")
            tx("gone", "acc-a", "11-01T00:00:00Z", deletedAt = 3L)
            tx("in-live", "acc-c", "11-01T00:00:00Z", type = TransactionType.TRANSFER, toAccountId = "acc-a")
            tx(
                "out-gone",
                "acc-a",
                "11-01T00:00:00Z",
                type = TransactionType.TRANSFER,
                toAccountId = "acc-c",
                deletedAt = 3L,
            )
            tx(
                "in-gone",
                "acc-c",
                "11-01T00:00:00Z",
                type = TransactionType.TRANSFER,
                toAccountId = "acc-a",
                deletedAt = 3L,
            )
            tx("other", "acc-c", "11-01T00:00:00Z")

            repo.merge("acc-a", "acc-b", NOW)

            assertEquals("acc-b", txOf("live").accountId)
            assertEquals("acc-b", txOf("gone").accountId)
            assertEquals("acc-b", txOf("in-live").toAccountId)
            assertEquals("acc-b", txOf("in-gone").toAccountId)
            assertEquals("acc-b", txOf("out-gone").accountId)
            assertEquals(NOW, txOf("live").updatedAt)
            assertEquals(NOW, txOf("in-gone").updatedAt)
            assertEquals(1_700_000_000_000L, txOf("other").updatedAt)
        }

    @Test
    fun `merge keeps the bank balance`() =
        runTest {
            bank("acc-a", balance = 5_000_000L)
            bank("acc-b", balance = 10_100_000L)
            tx("b1", "acc-b", "11-05T00:00:00Z", postBalance = 10_000_000L)
            tx("a1", "acc-a", "11-03T00:00:00Z", amount = 200_000L)

            repo.merge("acc-a", "acc-b", NOW)

            assertEquals(10_000_000L, rowOf("acc-b").currentBalance)
            assertEquals(10_300_000L, rowOf("acc-b").initialBalance)
        }

    @Test
    fun `merge sets an uninitialized target from the latest postBalance of either side`() =
        runTest {
            bank("acc-a")
            bank("acc-b", initialized = false)
            tx("b1", "acc-b", "11-02T00:00:00Z", amount = 20_000L, postBalance = 6_900_000L)
            tx(
                "a1",
                "acc-a",
                "11-06T00:00:00Z",
                amount = 50_000L,
                type = TransactionType.INCOME,
                postBalance = 7_000_000L,
            )
            tx("a2", "acc-a", "11-08T00:00:00Z", amount = 10_000L)

            repo.merge("acc-a", "acc-b", NOW)

            val target = rowOf("acc-b")
            assertTrue(target.isInitialized)
            assertEquals(6_970_000L, target.initialBalance)
            assertEquals(6_990_000L, target.currentBalance)
        }

    @Test
    fun `merge after Adjust keeps balance and books no drift`() =
        runTest {
            bank("acc-a")
            bank("acc-b", balance = 1_000_000L)
            tx("b1", "acc-b", "11-01T00:00:00Z", postBalance = 800_000L)
            TransactionRepositoryImpl(dbRule.db, clock).adjustDrift("adj-1", "acc-b", clock.millis())

            repo.merge("acc-a", "acc-b", NOW)

            val target = rowOf("acc-b")
            assertEquals(800_000L, target.currentBalance)
            assertEquals(1_000_000L, target.initialBalance)
            val rows =
                dbRule.db
                    .transactionDao()
                    .getActiveByAccount("acc-b")
                    .map { it.toDomain() }
            assertNull(detectBalanceDrift(target.toDomain(), rows))
        }

    @Test
    fun `received transfer post balance does not re-anchor`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            bank("acc-c", pkg = "other")
            tx("b1", "acc-b", "11-01T00:00:00Z", postBalance = 500L)
            tx(
                "in1",
                "acc-c",
                "11-05T00:00:00Z",
                type = TransactionType.TRANSFER,
                toAccountId = "acc-b",
                postBalance = 9_999L,
            )

            repo.merge("acc-a", "acc-b", NOW)

            assertEquals(100_500L, rowOf("acc-b").initialBalance)
            assertEquals(100_500L, rowOf("acc-b").currentBalance)
        }

    @Test
    fun `merge keeps a credit card start`() =
        runTest {
            bank("acc-a")
            bank("acc-b", type = AccountType.CREDIT_CARD, balance = -500_000L)
            tx("b1", "acc-b", "11-05T00:00:00Z", postBalance = 123L)

            repo.merge("acc-a", "acc-b", NOW)

            assertEquals(-500_000L, rowOf("acc-b").initialBalance)
            assertEquals(-600_000L, rowOf("acc-b").currentBalance)
        }

    @Test
    fun `merge without any postBalance copies an initialized source start`() =
        runTest {
            bank("acc-a", balance = 3_000_000L)
            bank("acc-b", balance = 1_000_000L, initialized = false)
            tx("a1", "acc-a", "11-03T00:00:00Z")

            repo.merge("acc-a", "acc-b", NOW)

            assertTrue(rowOf("acc-b").isInitialized)
            assertEquals(3_000_000L, rowOf("acc-b").initialBalance)
            assertEquals(2_900_000L, rowOf("acc-b").currentBalance)
        }

    @Test
    fun `merge without any postBalance keeps the target start`() =
        runTest {
            bank("acc-a", balance = 3_000_000L)
            bank("acc-b", balance = 1_000_000L)
            tx("a1", "acc-a", "11-03T00:00:00Z")

            repo.merge("acc-a", "acc-b", NOW)

            assertEquals(1_000_000L, rowOf("acc-b").initialBalance)
            assertEquals(900_000L, rowOf("acc-b").currentBalance)
        }

    @Test
    fun `merge deletes source snapshots and rebuilds target snapshots`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            tx("a1", "acc-a", "09-10T00:00:00Z")
            dbRule.db.accountBalanceSnapshotDao().insert(
                snapshot(id = "snap-a", accountId = "acc-a", monthYear = "2026-09"),
            )
            dbRule.db.accountBalanceSnapshotDao().insert(
                snapshot(id = "snap-b", accountId = "acc-b", monthYear = "2026-01"),
            )

            repo.merge("acc-a", "acc-b", NOW)

            assertTrue(snapshotMonths("acc-a").isEmpty())
            assertEquals(listOf("2026-01", "2026-09", "2026-10", "2026-11"), snapshotMonths("acc-b"))
        }

    @Test
    fun `failure after the move rolls everything back`() =
        runTest {
            val source = bank("acc-a")
            val target = bank("acc-b", balance = Long.MAX_VALUE - 10L)
            val moved = tx("a1", "acc-a", "11-01T00:00:00Z", type = TransactionType.INCOME)
            dbRule.db.accountBalanceSnapshotDao().insert(
                snapshot(id = "snap-a", accountId = "acc-a", monthYear = "2026-09"),
            )

            val failure = runCatching { repo.merge("acc-a", "acc-b", NOW) }.exceptionOrNull()

            // The rebuild overflows after the transactions were moved.
            assertTrue(failure.toString(), failure is ArithmeticException)
            assertEquals(moved, txOf("a1"))
            assertEquals(source, rowOf("acc-a"))
            assertNull(rowOf("acc-a").mergedIntoId)
            assertEquals(target, rowOf("acc-b"))
            assertEquals(listOf("2026-09"), snapshotMonths("acc-a"))
        }

    @Test
    fun `merge soft-deletes the source with mergedIntoId`() =
        runTest {
            bank("acc-a")
            bank("acc-b")

            repo.merge("acc-a", "acc-b", NOW)

            val source = rowOf("acc-a")
            assertEquals(NOW, source.deletedAt)
            assertEquals(NOW, source.updatedAt)
            assertEquals("acc-b", source.mergedIntoId)
            assertNull(rowOf("acc-b").deletedAt)
            assertNull(rowOf("acc-b").mergedIntoId)
        }

    @Test
    fun `chained merge repoints older rows`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            bank("acc-c")

            repo.merge("acc-a", "acc-b", NOW)
            repo.merge("acc-b", "acc-c", NOW + 1)

            assertEquals("acc-c", rowOf("acc-a").mergedIntoId)
            assertEquals("acc-c", rowOf("acc-b").mergedIntoId)
            assertNull(rowOf("acc-c").mergedIntoId)
            assertNotNull(repo.getAccount("acc-a"))
        }

    @Test
    fun `same id is rejected`() =
        runTest {
            bank("acc-a")

            assertRejected(MergeRejection.SAME_ACCOUNT, "acc-a", "acc-a")
        }

    @Test
    fun `deleted or unknown account is rejected`() =
        runTest {
            bank("acc-a", deletedAt = 5L)
            bank("acc-b")

            assertRejected(MergeRejection.ACCOUNT_GONE, "acc-a", "acc-b")
            assertRejected(MergeRejection.ACCOUNT_GONE, "acc-b", "acc-a")
            assertRejected(MergeRejection.ACCOUNT_GONE, "acc-b", "ghost")
            assertRejected(MergeRejection.ACCOUNT_GONE, "ghost", "acc-b")
        }

    @Test
    fun `other or null package is rejected`() =
        runTest {
            bank("acc-a")
            bank("acc-b", pkg = "other")
            bank("acc-c", pkg = null)
            bank("acc-d", pkg = null)

            assertRejected(MergeRejection.DIFFERENT_BANK, "acc-a", "acc-b")
            assertRejected(MergeRejection.DIFFERENT_BANK, "acc-a", "acc-c")
            assertRejected(MergeRejection.DIFFERENT_BANK, "acc-c", "acc-a")
            assertRejected(MergeRejection.DIFFERENT_BANK, "acc-c", "acc-d")
        }

    @Test
    fun `live transfer between the two is rejected`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            tx("t1", "acc-b", "11-01T00:00:00Z", type = TransactionType.TRANSFER, toAccountId = "acc-a")
            tx("e1", "acc-a", "11-02T00:00:00Z")

            assertRejected(MergeRejection.HAS_TRANSFER, "acc-a", "acc-b", listOf("t1", "e1"))
            assertRejected(MergeRejection.HAS_TRANSFER, "acc-b", "acc-a", listOf("t1", "e1"))
        }

    @Test
    fun `deleted transfer between the two is rejected`() =
        runTest {
            bank("acc-a")
            bank("acc-b")
            tx("t1", "acc-a", "11-01T00:00:00Z", type = TransactionType.TRANSFER, toAccountId = "acc-b", deletedAt = 3L)

            assertRejected(MergeRejection.HAS_TRANSFER, "acc-a", "acc-b", listOf("t1"))
        }

    @Test
    fun `getLiveTransactionCount counts source and destination once and skips deleted`() =
        runTest {
            bank("acc-a")
            bank("acc-b", pkg = "other")
            tx("e1", "acc-a", "11-01T00:00:00Z")
            tx("e2", "acc-a", "11-01T00:00:00Z", deletedAt = 3L)
            tx("t1", "acc-b", "11-01T00:00:00Z", type = TransactionType.TRANSFER, toAccountId = "acc-a")

            assertEquals(2, repo.getLiveTransactionCount("acc-a"))
        }

    private companion object {
        const val PKG = "com.vcb"
        const val NOW = 1_800_000_000_000L
    }
}
