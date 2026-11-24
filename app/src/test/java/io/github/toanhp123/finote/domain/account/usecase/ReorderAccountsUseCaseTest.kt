package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ReorderAccountsUseCaseTest {
    private val now = Instant.parse("2026-11-03T00:00:00Z")
    private val repo = FakeAccountRepository()
    private val reorder = ReorderAccountsUseCase(repo, FakeTransactionRunner(), Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `reorder passes the ids in order`() =
        runTest {
            listOf("a", "b", "c").forEach { repo.stored[it] = testAccount(id = it) }

            assertEquals(Outcome.Ok(Unit), reorder(listOf("c", "a", "b")))

            assertEquals(
                listOf("c", "a", "b"),
                repo.stored.values
                    .sortedBy { it.sortOrder }
                    .map { it.id },
            )
            assertEquals(now.toEpochMilli(), repo.stored.getValue("c").updatedAt)
        }
}
