package io.github.toanhp123.finote.domain.account.usecase

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.toanhp123.finote.data.account.repository.AccountRepositoryImpl
import io.github.toanhp123.finote.data.database.InMemoryDatabaseRule
import io.github.toanhp123.finote.data.database.RoomTransactionRunner
import io.github.toanhp123.finote.data.database.account
import io.github.toanhp123.finote.data.database.transaction
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** The account use cases on the real Room runner: the check and the write share one transaction. */
@RunWith(AndroidJUnit4::class)
class AccountUseCaseTransactionTest {
    @get:Rule
    val dbRule = InMemoryDatabaseRule()

    private val clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC)
    private val accounts get() = dbRule.db.accountDao()
    private val transactions get() = dbRule.db.transactionDao()
    private val repo by lazy { AccountRepositoryImpl(dbRule.db, clock) }
    private val runner by lazy { RoomTransactionRunner(dbRule.db) }

    @Test
    fun `merge across banks returns MergeRejected(DIFFERENT_BANK) and moves nothing`() =
        runTest {
            accounts.insert(account(id = "src", bankPackageName = "bank.one"))
            accounts.insert(account(id = "dst", bankPackageName = "bank.two"))
            transactions.insert(transaction(id = "tx-1", accountId = "src"))
            val before = checkNotNull(accounts.getById("src"))

            val result = MergeAccountsUseCase(repo, runner, clock)("src", "dst")

            assertEquals(Outcome.Err(AccountError.MergeRejected(MergeRejection.DIFFERENT_BANK)), result)
            assertEquals(before, accounts.getById("src"))
            assertEquals("src", transactions.getById("tx-1")?.accountId)
        }

    @Test
    fun `delete with a live transaction returns HasTransactions`() =
        runTest {
            accounts.insert(account(id = "acc-1"))
            transactions.insert(transaction(id = "tx-1", accountId = "acc-1"))

            val result = DeleteAccountUseCase(repo, runner, clock)("acc-1")

            assertEquals(Outcome.Err(AccountError.HasTransactions), result)
            assertNull(accounts.getById("acc-1")?.deletedAt)
        }
}
