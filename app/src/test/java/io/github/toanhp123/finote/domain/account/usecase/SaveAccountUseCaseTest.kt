package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SaveAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val save = SaveAccountUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    init {
        repo.stored["acc-1"] = testAccount()
    }

    @Test
    fun `updatedAt is stamped and the save is one transaction`() =
        runTest {
            val outcome = save(testAccount(name = "Renamed").copy(updatedAt = 2L), startBalance = null)

            assertEquals(Outcome.Ok(Unit), outcome)
            val saved = repo.stored.getValue("acc-1")
            assertEquals("Renamed", saved.name)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `save without a start change keeps a provisional account provisional`() =
        runTest {
            save(testAccount(name = "Renamed"), startBalance = 1_000_000L)
            save(testAccount(name = "Again"), startBalance = null)

            val saved = repo.stored.getValue("acc-1")
            assertEquals(false, saved.isInitialized)
            assertEquals(1_000_000L, saved.initialBalance)
        }

    @Test
    fun `save with a changed start fixes a provisional account`() =
        runTest {
            save(testAccount(), startBalance = 2_500_000L)

            val saved = repo.stored.getValue("acc-1")
            assertEquals(true, saved.isInitialized)
            assertEquals(2_500_000L, saved.initialBalance)
            assertEquals(2_500_000L, saved.currentBalance)
        }

    @Test
    fun `save ignores the start of an initialized account`() =
        runTest {
            repo.stored["acc-1"] = testAccount().copy(isInitialized = true)

            save(testAccount(), startBalance = 9L)

            assertEquals(1_000_000L, repo.stored.getValue("acc-1").initialBalance)
        }

    @Test
    fun `save links the bank app only while none is stored`() =
        runTest {
            save(testAccount().copy(bankPackageName = "bank.one"), startBalance = null)
            save(testAccount().copy(bankPackageName = "bank.two"), startBalance = null)

            assertEquals("bank.one", repo.stored.getValue("acc-1").bankPackageName)
        }

    @Test
    fun `blank name is BlankName and nothing is written`() =
        runTest {
            assertEquals(Outcome.Err(AccountError.BlankName), save(testAccount(name = " "), startBalance = null))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `currency is not editable, so a different one is not an error`() =
        runTest {
            assertEquals(Outcome.Ok(Unit), save(testAccount(currency = "USD"), startBalance = null))
        }

    @Test
    fun `save of a missing or deleted account is NotFound and nothing is written`() =
        runTest {
            repo.stored["gone"] = testAccount(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(AccountError.NotFound), save(testAccount(id = "ghost"), startBalance = 1L))
            assertEquals(Outcome.Err(AccountError.NotFound), save(testAccount(id = "gone"), startBalance = 1L))
            assertEquals(0, repo.writes)
        }
}
