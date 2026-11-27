package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.DeleteImpact
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

            assertEquals(2, count("a").transactions)
            assertEquals(1, count("b").transactions)
        }

    @Test
    fun `count includes the rules of the branch`() =
        runTest {
            repo.stored["a"] = testCategory(id = "a")
            repo.stored["a1"] = testCategory(id = "a1", parentId = "a")
            repo.stored["b"] = testCategory(id = "b")
            repo.ruleCategory["r-parent"] = "a"
            repo.ruleCategory["r-child"] = "a1"
            repo.ruleCategory["r-other"] = "b"

            assertEquals(DeleteImpact(transactions = 0, rules = 2, budgets = 0), count("a"))
        }

    @Test
    fun `count includes budgets of the branch`() =
        runTest {
            repo.stored["a"] = testCategory(id = "a")
            repo.stored["a1"] = testCategory(id = "a1", parentId = "a")
            repo.stored["b"] = testCategory(id = "b")
            repo.budgetCategory["b-oct"] = "a"
            repo.budgetCategory["b-nov"] = "a"
            repo.budgetCategory["b-other"] = "b"

            assertEquals(DeleteImpact(transactions = 0, rules = 0, budgets = 2), count("a"))
        }

    @Test
    fun `count of an unknown id is zero`() =
        runTest {
            assertEquals(DeleteImpact(transactions = 0, rules = 0, budgets = 0), count("ghost"))
        }
}
