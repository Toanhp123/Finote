package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.testAccount
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
    private val create = CreateAccountUseCase(repo, ValidateAccountUseCase(), Clock.fixed(now, ZoneOffset.UTC))

    private suspend fun assertRejected(account: Account) {
        val failure = runCatching { create(account) }.exceptionOrNull()
        assertTrue(failure.toString(), failure is IllegalArgumentException)
        assertEquals(0, repo.writes)
    }

    @Test
    fun `blank name is rejected`() =
        runTest {
            assertRejected(testAccount(name = ""))
            assertRejected(testAccount(name = "   "))
        }

    @Test
    fun `non VND currency is rejected`() =
        runTest {
            assertRejected(testAccount(currency = "USD"))
        }

    @Test
    fun `valid account is stored with clock timestamps, isInitialized true, currentBalance equal to initialBalance`() =
        runTest {
            create(testAccount().copy(currentBalance = 5L, deletedAt = 7L, createdAt = 1L, updatedAt = 2L))

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
