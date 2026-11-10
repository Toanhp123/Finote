package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveUsableAccountsUseCaseTest {
    private val repo = FakeAccountRepository()
    private val observeUsable = ObserveUsableAccountsUseCase(ObserveAccountsUseCase(repo))

    @Test
    fun `drops deleted, archived and ignored, keeps order`() =
        runTest {
            val rows =
                listOf(
                    testAccount(id = "b"),
                    testAccount(id = "gone").copy(deletedAt = 5L),
                    testAccount(id = "archived").copy(isArchived = true),
                    testAccount(id = "ignored").copy(isIgnored = true),
                    testAccount(id = "a"),
                )
            rows.forEach { repo.stored[it.id] = it }

            assertEquals(listOf("b", "a"), observeUsable().first().map { it.id })
        }
}
