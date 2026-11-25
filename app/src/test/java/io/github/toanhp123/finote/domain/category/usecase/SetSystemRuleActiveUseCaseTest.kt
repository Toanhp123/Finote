package io.github.toanhp123.finote.domain.category.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SetSystemRuleActiveUseCaseTest {
    private val now = Instant.parse("2026-11-25T00:00:00Z")
    private val user = testRule(id = "u-1")
    private val system = testRule(id = "kw_kfc", keyword = "kfc", isUserDefined = false, updatedAt = 0L)
    private val rules = FakeKeywordRuleRepository(listOf(user, system))
    private val setActive = SetSystemRuleActiveUseCase(rules, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `switches a system rule off and on and stamps updatedAt`() =
        runTest {
            setActive("kw_kfc", false)
            assertEquals(system.copy(isActive = false, updatedAt = now.toEpochMilli()), rules.getRule("kw_kfc"))

            setActive("kw_kfc", true)
            assertEquals(system.copy(isActive = true, updatedAt = now.toEpochMilli()), rules.getRule("kw_kfc"))
        }

    @Test
    fun `a user rule and an unknown id are left alone`() =
        runTest {
            setActive("u-1", false)
            setActive("missing", false)

            assertEquals(listOf(user, system), rules.rules)
        }
}
