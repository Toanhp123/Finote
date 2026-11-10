package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetLastManualChoicesUseCaseTest {
    @Test
    fun `reads account and both category types`() =
        runTest {
            val repo = FakeTransactionRepository()
            repo.lastAccountId = "acc"
            repo.lastCategoryIds[TransactionType.EXPENSE] = "c1"

            val choices = GetLastManualChoicesUseCase(repo)()

            assertEquals("acc", choices.accountId)
            assertEquals(mapOf(TransactionType.EXPENSE to "c1", TransactionType.INCOME to null), choices.categoryIds)
        }
}
