package io.github.toanhp123.finote.domain.account.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SetAccountIgnoredUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val setIgnored = SetAccountIgnoredUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `passes now from the clock`() =
        runTest {
            setIgnored("acc-1", ignored = true, shouldTrashOldTransactions = true)

            assertEquals(
                listOf(FakeAccountRepository.IgnoreCall("acc-1", true, true, now.toEpochMilli())),
                repo.ignores,
            )
        }
}
