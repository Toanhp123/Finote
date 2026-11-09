package io.github.toanhp123.finote.domain.account.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class MergeAccountsUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val merge = MergeAccountsUseCase(repo, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `passes now from the clock`() =
        runTest {
            merge("acc-1", "acc-2")

            assertEquals(listOf(Triple("acc-1", "acc-2", now.toEpochMilli())), repo.merges)
        }
}
