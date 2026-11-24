package io.github.toanhp123.finote.domain.account.usecase

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveMergeSuggestionsUseCaseTest {
    private val accounts = FakeAccountRepository()
    private val merges = FakeAccountMergeRepository()
    private val dismissals = FakeMergeDismissalRepository()
    private val observe = ObserveMergeSuggestionsUseCase(accounts, merges, dismissals)
    private val dismiss = DismissMergeSuggestionUseCase(dismissals)

    private fun seed() {
        listOf("a", "b").forEach { accounts.stored[it] = testAccount(id = it).copy(bankPackageName = "pkg.bank") }
    }

    @Test
    fun `suggests, then drops the pair once dismissed`() =
        runTest {
            seed()

            observe().test {
                assertEquals(mapOf("a" to "b", "b" to "a"), awaitItem())
                dismiss("b", "a")
                assertEquals(emptyMap<String, String>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a transfer between the two blocks the suggestion`() =
        runTest {
            seed()
            merges.transferCounts[setOf("a", "b")] = 1

            observe().test {
                assertEquals(emptyMap<String, String>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
