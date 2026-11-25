package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.testCategory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FindRenamingRuleUseCaseTest {
    private val categories =
        FakeCategoryRepository().apply {
            stored["food"] = testCategory(id = "food")
            stored["drink"] = testCategory(id = "drink")
            stored["salary"] = testCategory(id = "salary", type = CategoryType.INCOME)
        }
    private val onFood = testRule("kw_a", "highlands", "Highlands Coffee", "food", isUserDefined = false)
    private val onDrink = testRule("u_b", "highlands coffee", "Highlands Coffee", "drink")
    private val find = FindRenamingRuleUseCase(FakeKeywordRuleRepository(listOf(onDrink, onFood)), categories)

    @Test
    fun `finds the rule that gave the name, preferring the start category`() =
        runTest {
            assertEquals(onFood, find("Highlands Coffee", CategoryType.EXPENSE, "food"))
            assertEquals(onDrink, find("Highlands Coffee", CategoryType.EXPENSE, "drink"))
        }

    @Test
    fun `falls back to any rule of the type when none has the start category`() =
        runTest {
            assertEquals(onDrink, find("Highlands Coffee", CategoryType.EXPENSE, null))
        }

    @Test
    fun `no rule for another name, another type or an inactive rule`() =
        runTest {
            val off = FakeKeywordRuleRepository(listOf(onFood.copy(isActive = false)))

            assertNull(find("Other", CategoryType.EXPENSE, "food"))
            assertNull(find("Highlands Coffee", CategoryType.INCOME, "food"))
            assertNull(FindRenamingRuleUseCase(off, categories)("Highlands Coffee", CategoryType.EXPENSE, "food"))
        }
}
