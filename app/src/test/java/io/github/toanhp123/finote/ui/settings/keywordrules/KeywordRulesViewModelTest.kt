package io.github.toanhp123.finote.ui.settings.keywordrules

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeKeywordRuleRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveKeywordRulesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SetSystemRuleActiveUseCase
import io.github.toanhp123.finote.domain.category.usecase.testRule
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class KeywordRulesViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val categories = FakeCategoryRepository().apply { stored["food"] = testCategory(id = "food") }

    private fun TestScope.loaded(vararg list: KeywordRule): Pair<KeywordRulesViewModel, FakeKeywordRuleRepository> {
        val rules = FakeKeywordRuleRepository(list.toList())
        val vm =
            KeywordRulesViewModel(
                ObserveKeywordRulesUseCase(rules),
                ObserveCategoriesUseCase(categories),
                SetSystemRuleActiveUseCase(rules, clock),
                isDebuggable = true,
            )
        runCurrent()
        return vm to rules
    }

    private fun system(id: String) = testRule(id = id, keyword = "k-$id", isUserDefined = false, categoryId = "food")

    @Test
    fun `user rules come before system rules`() =
        runTest(mainRule.dispatcher) {
            val (vm, _) =
                loaded(
                    system("s-1"),
                    testRule(id = "u-1", createdAt = 1L, categoryId = "food"),
                    testRule(id = "u-2", keyword = "highlands", createdAt = 2L, categoryId = "food"),
                )

            // User rules are their own list, newest first, so the screen draws them above the system ones.
            assertEquals(
                listOf("u-2", "u-1"),
                vm.state.value.user
                    .map { it.rule.id },
            )
            assertEquals(
                listOf("s-1"),
                vm.state.value.system
                    .map { it.rule.id },
            )
            assertEquals(
                "food",
                vm.state.value.user
                    .first()
                    .category
                    ?.id,
            )
        }

    @Test
    fun `a rule whose category is gone still lists`() =
        runTest(mainRule.dispatcher) {
            val (vm, _) = loaded(testRule(id = "u-1", categoryId = "gone"))

            assertNull(
                vm.state.value.user
                    .single()
                    .category,
            )
        }

    @Test
    fun `switch toggles a system rule`() =
        runTest(mainRule.dispatcher) {
            val (vm, rules) = loaded(system("s-1"))

            vm.onIntent(KeywordRulesIntent.SystemRuleToggled("s-1", isActive = false))
            runCurrent()

            assertFalse(rules.rules.single().isActive)
            assertFalse(
                vm.state.value.system
                    .single()
                    .rule.isActive,
            )
        }

    @Test
    fun `a click opens the edit screen and add opens a blank one`() =
        runTest(mainRule.dispatcher) {
            val (vm, _) = loaded(testRule(id = "u-1", categoryId = "food"))

            vm.effects.test {
                vm.onIntent(KeywordRulesIntent.RuleClicked("u-1"))
                vm.onIntent(KeywordRulesIntent.AddClicked)
                runCurrent()

                assertEquals(KeywordRulesEffect.OpenEditRule(EditKeywordRule("u-1")), awaitItem())
                assertEquals(KeywordRulesEffect.OpenEditRule(EditKeywordRule()), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `no user rule leaves the user list empty`() =
        runTest(mainRule.dispatcher) {
            val (vm, _) = loaded(system("s-1"))

            assertTrue(
                vm.state.value.user
                    .isEmpty(),
            )
        }
}
