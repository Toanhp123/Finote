package io.github.toanhp123.finote.domain.transaction.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.transaction.repository.TransactionRepositoryImpl
import io.github.toanhp123.finote.data.transaction.repository.domainTx
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The transaction use cases on the real Room runner: the check and the write share one transaction. */
@RunWith(AndroidJUnit4::class)
class TransactionUseCaseTransactionTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
    private val accounts get() = dbRule.db.accountDao()
    private val transactions get() = dbRule.db.transactionDao()
    private val snapshots get() = dbRule.db.accountBalanceSnapshotDao()
    private val repo by lazy { TransactionRepositoryImpl(dbRule.db, clock) }
    private val runner by lazy { RoomTransactionRunner(dbRule.db) }

    @Test
    fun `add to an archived account returns AccountUnusable and writes nothing`() =
        runTest {
            accounts.insert(account(id = "acc-1").copy(isArchived = true))
            val before = checkNotNull(accounts.getById("acc-1"))

            val result = AddTransactionUseCase(repo, runner, clock)(domainTx(at = "2026-11-02T00:00:00Z"))

            assertEquals(Outcome.Err(TransactionError.AccountUnusable), result)
            assertNull(transactions.getById("tx-1"))
            assertEquals(before, accounts.getById("acc-1"))
            assertEquals(emptyList<Any>(), snapshots.getByAccount("acc-1"))
        }

    @Test
    fun `overflowing add returns AmountOverflow and leaves balance and snapshots unchanged`() =
        runTest {
            accounts.insert(account(id = "acc-1", currentBalance = Long.MAX_VALUE - 100L))
            val before = checkNotNull(accounts.getById("acc-1"))

            val result =
                AddTransactionUseCase(repo, runner, clock)(
                    domainTx(type = TransactionType.INCOME, amount = 1_000L, at = "2026-11-02T00:00:00Z"),
                )

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), result)
            assertNull(transactions.getById("tx-1"))
            assertEquals(before, accounts.getById("acc-1"))
            assertEquals(emptyList<Any>(), snapshots.getByAccount("acc-1"))
        }

    @Test
    fun `overflowing update returns AmountOverflow and leaves the row, balance and snapshots unchanged`() =
        runTest {
            accounts.insert(account(id = "acc-1", currentBalance = Long.MAX_VALUE - 100L))
            val income = domainTx(type = TransactionType.INCOME, amount = 50L, at = "2026-11-02T00:00:00Z")
            assertEquals(Outcome.Ok(Unit), AddTransactionUseCase(repo, runner, clock)(income))
            val accountBefore = checkNotNull(accounts.getById("acc-1"))
            val rowBefore = checkNotNull(transactions.getById("tx-1"))
            val snapshotsBefore = snapshots.getByAccount("acc-1")

            val result = UpdateTransactionUseCase(repo, runner, clock)(income.copy(amount = 1_000L))

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), result)
            assertEquals(rowBefore, transactions.getById("tx-1"))
            assertEquals(accountBefore, accounts.getById("acc-1"))
            assertEquals(snapshotsBefore, snapshots.getByAccount("acc-1"))
        }

    @Test
    fun `overflowing delete returns AmountOverflow and leaves the row, balance and snapshots unchanged`() =
        runTest {
            accounts.insert(account(id = "acc-1", currentBalance = Long.MIN_VALUE + 1_000L))
            val add = AddTransactionUseCase(repo, runner, clock)
            val at = "2026-11-02T00:00:00Z"
            val income = domainTx(id = "tx-1", type = TransactionType.INCOME, amount = 1_500L, at = at)
            val expense = domainTx(id = "tx-2", type = TransactionType.EXPENSE, amount = 2_000L, at = at)
            assertEquals(Outcome.Ok(Unit), add(income))
            assertEquals(Outcome.Ok(Unit), add(expense))
            val accountBefore = checkNotNull(accounts.getById("acc-1"))
            val rowBefore = checkNotNull(transactions.getById("tx-1"))
            val snapshotsBefore = snapshots.getByAccount("acc-1")

            val result = DeleteTransactionUseCase(repo, runner, clock)("tx-1")

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), result)
            assertEquals(rowBefore, transactions.getById("tx-1"))
            assertEquals(accountBefore, accounts.getById("acc-1"))
            assertEquals(snapshotsBefore, snapshots.getByAccount("acc-1"))
        }

    @Test
    fun `overflowing reconcile returns AmountOverflow and writes no adjustment`() =
        runTest {
            accounts.insert(account(id = "acc-1", currentBalance = 1_000_000L))
            val accountBefore = checkNotNull(accounts.getById("acc-1"))

            val result = ReconcileBalanceUseCase(repo, runner, clock)("acc-1", Long.MIN_VALUE)

            assertEquals(Outcome.Err(TransactionError.AmountOverflow), result)
            assertEquals(accountBefore, accounts.getById("acc-1"))
            assertEquals(emptyList<Any>(), transactions.getActiveByAccount("acc-1"))
            assertEquals(emptyList<Any>(), snapshots.getByAccount("acc-1"))
        }
}
