package io.github.toanhp123.finote.domain.category.usecase

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ObserveKeywordRulesUseCaseTest {
    @Test
    fun `emits the rules again after a change`() =
        runTest {
            val rules = FakeKeywordRuleRepository(listOf(testRule(id = "u-1")))

            ObserveKeywordRulesUseCase(rules)().test {
                assertEquals(listOf("u-1"), awaitItem().map { it.id })

                rules.upsertUserRule(testRule(id = "u-2", keyword = "kfc"))

                assertEquals(listOf("u-1", "u-2"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
