package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class CreateAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val runner = FakeTransactionRunner()
    private val create = CreateAccountUseCase(repo, runner, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `blank name is BlankName and nothing is stored`() =
        runTest {
            assertEquals(Outcome.Err(AccountError.BlankName), create(testAccount(name = "")))
            assertEquals(Outcome.Err(AccountError.BlankName), create(testAccount(name = "   ")))
            assertEquals(0, repo.writes)
            assertTrue(repo.stored.isEmpty())
        }

    @Test
    fun `non VND currency is UnsupportedCurrency and nothing is stored`() =
        runTest {
            assertEquals(Outcome.Err(AccountError.UnsupportedCurrency), create(testAccount(currency = "USD")))
            assertEquals(0, repo.writes)
        }

    @Test
    fun `valid account is stored with clock timestamps, isInitialized true, currentBalance equal to initialBalance`() =
        runTest {
            val dirty = testAccount().copy(currentBalance = 5L, deletedAt = 7L, createdAt = 1L, updatedAt = 2L)

            val outcome = create(dirty)

            assertEquals(Outcome.Ok("acc-1"), outcome)
            assertEquals(1, runner.writes)
            val saved = repo.stored.getValue("acc-1")
            assertEquals(now.toEpochMilli(), saved.createdAt)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(true, saved.isInitialized)
            assertEquals(1_000_000L, saved.currentBalance)
            assertNull(saved.deletedAt)
        }

    @Test
    fun `negative initialBalance is accepted`() =
        runTest {
            create(testAccount(initialBalance = -300_000L))

            assertEquals(-300_000L, repo.stored.getValue("acc-1").currentBalance)
        }
}
