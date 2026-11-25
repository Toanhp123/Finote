package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.model.KeywordMatchType
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.common.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class SaveKeywordRuleUseCaseTest {
    private val now = Instant.parse("2026-11-25T00:00:00Z").toEpochMilli()
    private val categories =
        FakeCategoryRepository().apply {
            stored["food"] = testCategory(id = "food")
            stored["ride"] = testCategory(id = "ride")
            stored["salary"] = testCategory(id = "salary", type = CategoryType.INCOME)
            stored["hidden"] = testCategory(id = "hidden").copy(isHidden = true)
            stored["gone"] = testCategory(id = "gone").copy(deletedAt = 5L)
        }
    private val rules = FakeKeywordRuleRepository()
    private val runner = FakeTransactionRunner()
    private val save =
        SaveKeywordRuleUseCase(rules, categories, runner, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))

    private fun ok(outcome: Outcome<KeywordRule, KeywordRuleError>) = (outcome as Outcome.Ok).value

    @Test
    fun `new rule gets an id, the user shape and both timestamps`() =
        runTest {
            val rule = ok(save(null, "  Grab Food ", "  Grab  ", "food"))

            assertTrue(rule.id.isNotBlank())
            assertEquals("Grab Food", rule.keyword)
            assertEquals("grab food", rule.normalizedKeyword)
            assertEquals("Grab", rule.merchantName)
            assertEquals("food", rule.categoryId)
            assertEquals(KeywordMatchType.CONTAINS, rule.matchType)
            assertTrue(rule.isUserDefined)
            assertTrue(rule.isActive)
            assertEquals(now, rule.createdAt)
            assertEquals(now, rule.updatedAt)
            assertEquals(listOf(rule), rules.rules)
            assertEquals(1, runner.writes)
        }

    @Test
    fun `blank merchant name is stored as null`() =
        runTest {
            assertEquals(null, ok(save(null, "grab", "   ", "food")).merchantName)
        }

    @Test
    fun `update keeps the id and createdAt and moves updatedAt`() =
        runTest {
            rules.upsertUserRule(
                testRule(id = "r-1", keyword = "grab", categoryId = "food", createdAt = 7L, updatedAt = 7L),
            )

            val rule = ok(save("r-1", "Grab Bike", "Grab", "ride"))

            assertEquals("r-1", rule.id)
            assertEquals(7L, rule.createdAt)
            assertEquals(now, rule.updatedAt)
            assertEquals("grab bike", rule.normalizedKeyword)
            assertEquals("ride", rule.categoryId)
            assertEquals(listOf(rule), rules.rules)
        }

    @Test
    fun `update of the same rule with the same keyword is not a duplicate`() =
        runTest {
            rules.upsertUserRule(testRule(id = "r-1", keyword = "grab", categoryId = "food"))

            assertTrue(save("r-1", "GRAB", "Grab", "food") is Outcome.Ok)
        }

    @Test
    fun `same keyword and category type of another user rule is a duplicate`() =
        runTest {
            rules.upsertUserRule(testRule(id = "r-1", keyword = "grab", categoryId = "food"))

            // Same normalized keyword, different case, other category of the same type.
            assertEquals(Outcome.Err(KeywordRuleError.Duplicate), save(null, "GRAB", null, "ride"))
            rules.upsertUserRule(testRule(id = "r-2", keyword = "kfc", categoryId = "food"))
            assertEquals(Outcome.Err(KeywordRuleError.Duplicate), save("r-2", "Grab", null, "food"))
            assertEquals(2, rules.rules.size)
        }

    @Test
    fun `same keyword of the other type is not a duplicate`() =
        runTest {
            rules.upsertUserRule(testRule(id = "r-1", keyword = "grab", categoryId = "food"))

            assertTrue(save(null, "grab", null, "salary") is Outcome.Ok)
            assertEquals(2, rules.rules.size)
        }

    @Test
    fun `a system rule with the same keyword is not a duplicate`() =
        runTest {
            val system = testRule(id = "kw_grab", keyword = "grab", categoryId = "food", isUserDefined = false)
            val withSystem = FakeKeywordRuleRepository(listOf(system))
            val saveWithSystem =
                SaveKeywordRuleUseCase(
                    withSystem,
                    categories,
                    runner,
                    Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
                )

            assertTrue(saveWithSystem(null, "grab", null, "food") is Outcome.Ok)
            assertEquals(2, withSystem.rules.size)
        }

    @Test
    fun `keyword under two normalized characters is too short and nothing runs`() =
        runTest {
            assertEquals(Outcome.Err(KeywordRuleError.TooShort), save(null, "a", null, "food"))
            assertEquals(Outcome.Err(KeywordRuleError.TooShort), save(null, "  é  ", null, "food"))
            assertEquals(Outcome.Err(KeywordRuleError.TooShort), save(null, "   ", null, "food"))
            assertEquals(0, runner.writes)
            assertTrue(rules.rules.isEmpty())
        }

    @Test
    fun `two characters are enough`() =
        runTest {
            assertTrue(save(null, "be", null, "food") is Outcome.Ok)
        }

    @Test
    fun `missing, deleted or hidden category is CategoryGone`() =
        runTest {
            listOf("nope", "gone", "hidden").forEach {
                assertEquals(Outcome.Err(KeywordRuleError.CategoryGone), save(null, "grab", null, it))
            }
            assertTrue(rules.rules.isEmpty())
        }

    @Test
    fun `update of an unknown id or a system rule is NotFound and changes nothing`() =
        runTest {
            val system = testRule(id = "kw_kfc", keyword = "kfc", categoryId = "food", isUserDefined = false)
            val repo = FakeKeywordRuleRepository(listOf(system))
            val saveWithSystem =
                SaveKeywordRuleUseCase(repo, categories, runner, Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC))

            assertEquals(Outcome.Err(KeywordRuleError.NotFound), saveWithSystem("kw_kfc", "kfc", "KFC", "ride"))
            assertEquals(Outcome.Err(KeywordRuleError.NotFound), saveWithSystem("missing", "kfc", "KFC", "ride"))
            assertEquals(listOf(system), repo.rules)
        }
}
