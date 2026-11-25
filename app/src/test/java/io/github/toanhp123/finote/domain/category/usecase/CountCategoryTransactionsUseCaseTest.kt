package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CountCategoryTransactionsUseCaseTest {
    private val repo = FakeCategoryRepository()
    private val count = CountCategoryTransactionsUseCase(repo)

    @Test
    fun `count includes live children and ignores deleted transactions`() =
        runTest {
            repo.stored["a"] = testCategory(id = "a")
            repo.stored["a1"] = testCategory(id = "a1", parentId = "a")
            repo.stored["gone"] = testCategory(id = "gone", parentId = "a").copy(deletedAt = 5L)
            repo.stored["b"] = testCategory(id = "b")
            repo.transactionCategory["t-parent"] = "a"
            repo.transactionCategory["t-child"] = "a1"
            repo.transactionCategory["t-deleted"] = "a"
            repo.transactionCategory["t-of-deleted-child"] = "gone"
            repo.transactionCategory["t-other"] = "b"
            repo.deletedTransactions += "t-deleted"

            assertEquals(2, count("a"))
            assertEquals(1, count("b"))
        }

    @Test
    fun `count of an unknown id is zero`() =
        runTest {
            assertEquals(0, count("ghost"))
        }
}
