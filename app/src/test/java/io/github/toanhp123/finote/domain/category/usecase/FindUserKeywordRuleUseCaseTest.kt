package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindUserKeywordRuleUseCaseTest {
    private val categories =
        FakeCategoryRepository().apply {
            stored["food"] = testCategory(id = "food")
            stored["salary"] = testCategory(id = "salary", type = CategoryType.INCOME)
        }
    private val grab = testRule(id = "u-grab", keyword = "Grab", categoryId = "food")
    private val rules =
        FakeKeywordRuleRepository(
            listOf(
                grab,
                testRule(id = "kw_kfc", keyword = "kfc", categoryId = "food", isUserDefined = false),
            ),
        )
    private val find = FindUserKeywordRuleUseCase(rules, categories)

    @Test
    fun `finds the user rule of the same normalized keyword and type`() =
        runTest {
            assertEquals(grab, find("  GRAB ", CategoryType.EXPENSE))
        }

    @Test
    fun `the other type has no rule`() =
        runTest {
            assertNull(find("grab", CategoryType.INCOME))
        }

    @Test
    fun `a system rule is never returned`() =
        runTest {
            assertNull(find("kfc", CategoryType.EXPENSE))
        }
}
