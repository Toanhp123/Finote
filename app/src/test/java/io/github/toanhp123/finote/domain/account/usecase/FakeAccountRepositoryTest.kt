package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.testAccount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeAccountRepositoryTest {
    private val repository = FakeAccountRepository()

    @Test
    fun `observe sorts by sortOrder, createdAt, id`() =
        runTest {
            val y = testAccount(id = "y").copy(sortOrder = 0, createdAt = 1)
            val x = testAccount(id = "x").copy(sortOrder = 0, createdAt = 1)
            val z = testAccount(id = "z").copy(sortOrder = -1, createdAt = 1)
            val gone = testAccount(id = "gone").copy(sortOrder = -5, deletedAt = 7)
            listOf(y, x, z, gone).forEach { repository.add(it) }

            assertEquals(listOf("z", "x", "y"), repository.observeAccounts().first().map { it.id })
        }
}
