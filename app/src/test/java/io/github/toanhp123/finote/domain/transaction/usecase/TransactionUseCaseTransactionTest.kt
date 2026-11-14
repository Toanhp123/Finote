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
}
