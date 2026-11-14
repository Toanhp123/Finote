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

class UpdateAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val update = UpdateAccountUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    init {
        repo.stored["acc-1"] = testAccount()
    }

    @Test
    fun `updatedAt is stamped and isInitialized is unchanged`() =
        runTest {
            val outcome = update(testAccount(name = "Renamed").copy(updatedAt = 2L, isInitialized = false))

            assertEquals(Outcome.Ok(Unit), outcome)
            val saved = repo.stored.getValue("acc-1")
            assertEquals("Renamed", saved.name)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(false, repo.lastUpdateArg?.isInitialized)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `blank name is BlankName and nothing is written`() =
        runTest {
            assertEquals(Outcome.Err(AccountError.BlankName), update(testAccount(name = " ")))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `currency is not editable, so a different one is not an error`() =
        runTest {
            assertEquals(Outcome.Ok(Unit), update(testAccount(currency = "USD")))
            assertEquals(1, repo.writes)
        }

    @Test
    fun `missing or deleted account is NotFound and nothing is written`() =
        runTest {
            repo.stored["gone"] = testAccount(id = "gone").copy(deletedAt = 5L)

            assertEquals(Outcome.Err(AccountError.NotFound), update(testAccount(id = "ghost")))
            assertEquals(Outcome.Err(AccountError.NotFound), update(testAccount(id = "gone")))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `negative initialBalance is accepted`() =
        runTest {
            update(testAccount(initialBalance = -300_000L))

            assertEquals(1, repo.writes)
        }
}
