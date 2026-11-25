package io.github.toanhp123.finote.domain.category.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeleteKeywordRuleUseCaseTest {
    private val user = testRule(id = "u-1")
    private val system = testRule(id = "kw_kfc", keyword = "kfc", isUserDefined = false)
    private val rules = FakeKeywordRuleRepository(listOf(user, system))
    private val delete = DeleteKeywordRuleUseCase(rules)

    @Test
    fun `deletes a user rule`() =
        runTest {
            delete("u-1")

            assertEquals(listOf(system), rules.rules)
        }

    @Test
    fun `delete of a system rule does nothing`() =
        runTest {
            delete("kw_kfc")

            assertEquals(listOf(user, system), rules.rules)
        }
}
