package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CountRuleMatchesUseCaseTest {
    private val categories = FakeCategoryRepository().apply { stored["food"] = testCategory(id = "food") }
    private val rows =
        listOf(
            testRow(id = "t-1", merchantName = "GRAB VN"),
            testRow(id = "t-2", merchantName = null, note = "thanh toan grab"),
            testRow(id = "t-3", merchantName = "HIGHLANDS"),
        )
    private val rule = testRule(keyword = "grab", categoryId = "food")

    @Test
    fun `count equals the rows the apply writes`() =
        runTest {
            val repo = FakeKeywordRuleRepository(uncategorized = rows)

            val counted = CountRuleMatchesUseCase(repo, categories)(rule)
            val applied = ApplyRuleToPastUseCase(repo, categories, FakeTransactionRunner())(rule)

            assertEquals(2, counted)
            assertEquals(counted, applied)
            assertEquals(counted, repo.categorized.size)
        }

    @Test
    fun `count writes nothing`() =
        runTest {
            val repo = FakeKeywordRuleRepository(uncategorized = rows)

            CountRuleMatchesUseCase(repo, categories)(rule)

            assertEquals(emptyList<Any>(), repo.categorized)
        }

    @Test
    fun `a hidden category counts nothing`() =
        runTest {
            categories.stored["food"] = testCategory(id = "food").copy(isHidden = true)
            val repo = FakeKeywordRuleRepository(uncategorized = rows)

            assertEquals(0, CountRuleMatchesUseCase(repo, categories)(rule))
        }
}
