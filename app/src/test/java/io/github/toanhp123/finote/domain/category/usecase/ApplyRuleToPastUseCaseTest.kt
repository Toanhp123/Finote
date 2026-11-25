package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyRuleToPastUseCaseTest {
    private val categories =
        FakeCategoryRepository().apply {
            stored["food"] = testCategory(id = "food")
            stored["salary"] = testCategory(id = "salary", type = CategoryType.INCOME)
        }
    private val runner = FakeTransactionRunner()

    private suspend fun apply(
        repo: FakeKeywordRuleRepository,
        rule: KeywordRule,
    ) = ApplyRuleToPastUseCase(repo, categories, runner)(rule)

    @Test
    fun `writes the matching rows in one transaction with the rule name`() =
        runTest {
            val repo =
                FakeKeywordRuleRepository(
                    uncategorized =
                        listOf(
                            testRow(id = "t-1", merchantName = "GRAB VN"),
                            testRow(id = "t-2", merchantName = null, note = "thanh toan grab"),
                            testRow(id = "t-3", merchantName = "HIGHLANDS"),
                        ),
                )
            val rule = testRule(keyword = "grab", merchantName = "Grab", categoryId = "food")

            assertEquals(2, apply(repo, rule))

            assertEquals(listOf("t-1", "t-2"), repo.categorized.map { it.first })
            assertEquals(listOf("food", "food"), repo.categorized.map { it.second.categoryId })
            assertEquals(listOf("Grab", "Grab"), repo.categorized.map { it.second.merchantName })
            assertEquals(1, runner.writes)
        }

    @Test
    fun `a rule without a name passes the row merchant on`() =
        runTest {
            val repo =
                FakeKeywordRuleRepository(
                    uncategorized =
                        listOf(
                            testRow(id = "t-1", merchantName = "GRAB VN"),
                            testRow(id = "t-2", merchantName = null, note = "grab"),
                        ),
                )

            apply(repo, testRule(keyword = "grab", merchantName = null, categoryId = "food"))

            // A null name makes the write keep the stored merchant (COALESCE).
            assertEquals(listOf("GRAB VN", null), repo.categorized.map { it.second.merchantName })
        }

    @Test
    fun `only the given rule is tried and a row of the other type is left`() =
        runTest {
            val repo =
                FakeKeywordRuleRepository(
                    rules = listOf(testRule(id = "u-2", keyword = "grab food", categoryId = "salary")),
                    uncategorized =
                        listOf(
                            testRow(id = "t-1", merchantName = "GRAB FOOD"),
                            testRow(id = "t-2", merchantName = "GRAB", type = TransactionType.INCOME),
                        ),
                )

            assertEquals(1, apply(repo, testRule(keyword = "grab", categoryId = "food")))

            assertEquals(listOf("t-1"), repo.categorized.map { it.first })
        }

    @Test
    fun `a hidden or deleted category writes nothing`() =
        runTest {
            val repo = FakeKeywordRuleRepository(uncategorized = listOf(testRow()))
            val rule = testRule(categoryId = "food")

            categories.stored["food"] = testCategory(id = "food").copy(isHidden = true)
            assertEquals(0, apply(repo, rule))

            categories.stored["food"] = testCategory(id = "food").copy(deletedAt = 9L)
            assertEquals(0, apply(repo, rule))

            assertEquals(emptyList<Any>(), repo.categorized)
        }
}
