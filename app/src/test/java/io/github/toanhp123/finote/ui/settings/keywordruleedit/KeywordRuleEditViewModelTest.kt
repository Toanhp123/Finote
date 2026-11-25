package io.github.toanhp123.finote.ui.settings.keywordruleedit

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.category.error.KeywordRuleError
import io.github.toanhp123.finote.domain.category.model.KeywordRule
import io.github.toanhp123.finote.domain.category.testCategory
import io.github.toanhp123.finote.domain.category.usecase.ApplyRuleToPastUseCase
import io.github.toanhp123.finote.domain.category.usecase.CountRuleMatchesUseCase
import io.github.toanhp123.finote.domain.category.usecase.DeleteKeywordRuleUseCase
import io.github.toanhp123.finote.domain.category.usecase.FakeCategoryRepository
import io.github.toanhp123.finote.domain.category.usecase.FakeKeywordRuleRepository
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveKeywordRulesUseCase
import io.github.toanhp123.finote.domain.category.usecase.SaveKeywordRuleUseCase
import io.github.toanhp123.finote.domain.category.usecase.testRow
import io.github.toanhp123.finote.domain.category.usecase.testRule
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import io.github.toanhp123.finote.ui.navigation.EditKeywordRule
import io.github.toanhp123.finote.ui.settings.keywordruleedit.logic.KeywordRuleDraft
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
class KeywordRuleEditViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val clock = Clock.fixed(Instant.ofEpochMilli(5_000L), ZoneOffset.UTC)
    private val categories =
        FakeCategoryRepository().apply {
            stored["food"] = testCategory(id = "food")
            stored["hidden"] = testCategory(id = "hidden").copy(isHidden = true)
        }
    private lateinit var rules: FakeKeywordRuleRepository

    /** Builds the view model over [stored] rules and [uncategorized] past rows, and lets it load. */
    private fun TestScope.loaded(
        key: EditKeywordRule,
        stored: List<KeywordRule> = emptyList(),
        uncategorized: List<Transaction> = emptyList(),
    ): KeywordRuleEditViewModel {
        rules = FakeKeywordRuleRepository(stored, uncategorized)
        val tx = FakeTransactionRunner()
        return KeywordRuleEditViewModel(
            key,
            ObserveKeywordRulesUseCase(rules),
            ObserveCategoriesUseCase(categories),
            SaveKeywordRuleUseCase(rules, categories, tx, clock),
            DeleteKeywordRuleUseCase(rules),
            CountRuleMatchesUseCase(rules, categories),
            ApplyRuleToPastUseCase(rules, categories, tx),
            isDebuggable = true,
        ).also { runCurrent() }
    }

    private fun KeywordRuleEditViewModel.edit(change: KeywordRuleDraft.() -> KeywordRuleDraft) {
        onIntent(KeywordRuleEditIntent.DraftChanged(checkNotNull(state.value.draft).change()))
    }

    /** The first effect that [action] causes. */
    private suspend fun TestScope.effectOf(
        vm: KeywordRuleEditViewModel,
        action: () -> Unit,
    ): KeywordRuleEditEffect {
        var effect: KeywordRuleEditEffect? = null
        vm.effects.test {
            action()
            runCurrent()
            effect = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return checkNotNull(effect)
    }

    @Test
    fun `edit fills the draft from the stored rule`() =
        runTest(mainRule.dispatcher) {
            val stored = testRule(id = "u-1", keyword = "grab", merchantName = "Grab", categoryId = "food")

            val vm = loaded(EditKeywordRule("u-1"), listOf(stored))

            assertEquals(KeywordRuleDraft("grab", "Grab", "food"), vm.state.value.draft)
        }

    @Test
    fun `save new rule with matches asks bulk apply`() =
        runTest(mainRule.dispatcher) {
            val rows = listOf(testRow("t-1", merchantName = "GRAB VN"), testRow("t-2", merchantName = "GRAB"))
            val vm = loaded(EditKeywordRule(), uncategorized = rows)
            vm.edit { copy(keyword = "grab", merchantName = "Grab", categoryId = "food") }

            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()

            assertEquals(2, vm.state.value.bulkApplyCount)
            assertEquals("grab", rules.rules.single().keyword)
            val effect = effectOf(vm) { vm.onIntent(KeywordRuleEditIntent.BulkApplyConfirmed) }
            assertEquals(KeywordRuleEditEffect.Close, effect)
            assertEquals(listOf("t-1", "t-2"), rules.categorized.map { it.first })
        }

    @Test
    fun `a new rule without matches closes at once`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule())
            vm.edit { copy(keyword = "grab", categoryId = "food") }

            val effect = effectOf(vm) { vm.onIntent(KeywordRuleEditIntent.SaveClicked) }

            assertEquals(KeywordRuleEditEffect.Close, effect)
            assertNull(vm.state.value.bulkApplyCount)
        }

    @Test
    fun `changing only the name does not ask`() =
        runTest(mainRule.dispatcher) {
            val stored = testRule(id = "u-1", keyword = "grab", categoryId = "food")
            val vm =
                loaded(EditKeywordRule("u-1"), listOf(stored), listOf(testRow("t-1", merchantName = "GRAB VN")))
            vm.edit { copy(merchantName = "Grab") }

            val effect = effectOf(vm) { vm.onIntent(KeywordRuleEditIntent.SaveClicked) }

            assertEquals(KeywordRuleEditEffect.Close, effect)
            assertNull(vm.state.value.bulkApplyCount)
            assertEquals("Grab", rules.rules.single().merchantName)
            assertTrue(rules.categorized.isEmpty())
        }

    @Test
    fun `a new keyword on a stored rule asks`() =
        runTest(mainRule.dispatcher) {
            val stored = testRule(id = "u-1", keyword = "grab", categoryId = "food")
            val vm =
                loaded(EditKeywordRule("u-1"), listOf(stored), listOf(testRow("t-1", merchantName = "HIGHLANDS")))
            vm.edit { copy(keyword = "highlands") }

            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()

            assertEquals(1, vm.state.value.bulkApplyCount)
        }

    @Test
    fun `dismissing bulk apply leaves the rows alone and closes`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule(), uncategorized = listOf(testRow("t-1", merchantName = "GRAB")))
            vm.edit { copy(keyword = "grab", categoryId = "food") }
            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()

            val effect = effectOf(vm) { vm.onIntent(KeywordRuleEditIntent.BulkApplyDismissed) }

            assertEquals(KeywordRuleEditEffect.Close, effect)
            assertTrue(rules.categorized.isEmpty())
        }

    @Test
    fun `duplicate shows error`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule(), listOf(testRule(id = "u-2", keyword = "grab", categoryId = "food")))
            vm.edit { copy(keyword = "GRAB", categoryId = "food") }

            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()

            assertEquals(KeywordRuleError.Duplicate, vm.state.value.error)
            assertFalse(vm.state.value.isSaving)
            assertEquals(1, rules.rules.size)
            vm.effects.test {
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an error clears when the user saves again`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule())
            vm.edit { copy(keyword = "g", categoryId = "food") }
            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()
            assertEquals(KeywordRuleError.TooShort, vm.state.value.error)

            vm.edit { copy(keyword = "grab") }
            vm.onIntent(KeywordRuleEditIntent.SaveClicked)
            runCurrent()

            assertNull(vm.state.value.error)
        }

    @Test
    fun `delete asks then removes`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule("u-1"), listOf(testRule(id = "u-1", categoryId = "food")))

            vm.onIntent(KeywordRuleEditIntent.DeleteClicked)
            runCurrent()
            assertTrue(vm.state.value.isDeleteOpen)
            assertEquals(1, rules.rules.size)

            val effect = effectOf(vm) { vm.onIntent(KeywordRuleEditIntent.DeleteConfirmed) }

            assertEquals(KeywordRuleEditEffect.Close, effect)
            assertTrue(rules.rules.isEmpty())
            assertFalse(vm.state.value.isDeleteOpen)
        }

    @Test
    fun `an unknown id or a system rule id closes the screen`() =
        runTest(mainRule.dispatcher) {
            val system = testRule(id = "s-1", isUserDefined = false)

            listOf("nope", "s-1").forEach { id ->
                val vm = loaded(EditKeywordRule(id), listOf(system))
                assertEquals(KeywordRuleEditEffect.Close, effectOf(vm) { })
            }
        }

    @Test
    fun `the picker offers shown categories and the hidden one of the rule`() =
        runTest(mainRule.dispatcher) {
            val create = loaded(EditKeywordRule())
            assertEquals(
                listOf("food"),
                create.state.value.categories
                    .map { it.id },
            )

            val edit = loaded(EditKeywordRule("u-1"), listOf(testRule(id = "u-1", categoryId = "hidden")))
            assertEquals(
                setOf("food", "hidden"),
                edit.state.value.categories
                    .map { it.id }
                    .toSet(),
            )
        }

    @Test
    fun `the draft and the state print neither keyword nor name`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule())
            vm.edit { copy(keyword = "secretkeyword", merchantName = "secretname", categoryId = "food") }

            val state = vm.state.value
            assertFalse("secret" in state.draft.toString())
            assertFalse("secret" in state.toString())
        }

    @Test
    fun `save needs a keyword and a category`() =
        runTest(mainRule.dispatcher) {
            val vm = loaded(EditKeywordRule())
            assertFalse(vm.state.value.isSaveEnabled)

            vm.edit { copy(keyword = "grab") }
            assertFalse(vm.state.value.isSaveEnabled)

            vm.edit { copy(categoryId = "food") }
            assertTrue(vm.state.value.isSaveEnabled)
        }
}
