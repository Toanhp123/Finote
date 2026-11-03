package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class UpdateAccountUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val update = UpdateAccountUseCase(repo, ValidateAccountUseCase(), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `updatedAt is stamped and isInitialized becomes true`() =
        runTest {
            update(testAccount(name = "Renamed").copy(updatedAt = 2L, isInitialized = false))

            val saved = repo.stored.getValue("acc-1")
            assertEquals("Renamed", saved.name)
            assertEquals(now.toEpochMilli(), saved.updatedAt)
            assertEquals(true, saved.isInitialized)
        }

    @Test
    fun `blank name or non VND currency is rejected before writing`() =
        runTest {
            val blank = runCatching { update(testAccount(name = " ")) }.exceptionOrNull()
            val usd = runCatching { update(testAccount(currency = "USD")) }.exceptionOrNull()

            assertTrue(blank.toString(), blank is IllegalArgumentException)
            assertTrue(usd.toString(), usd is IllegalArgumentException)
            assertEquals(0, repo.writes)
        }

    @Test
    fun `negative initialBalance is accepted`() =
        runTest {
            update(testAccount(initialBalance = -300_000L))

            assertEquals(-300_000L, repo.stored.getValue("acc-1").initialBalance)
        }
}
