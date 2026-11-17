package io.github.toanhp123.finote.ui.settings

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeParserRuleRepository : ParserRuleRepository {
        val rules = MutableStateFlow<List<ParserRule>>(emptyList())

        override suspend fun applyRules(rules: List<ParserRule>): Int = 0

        override fun observeRules(): Flow<List<ParserRule>> = rules
    }

    private val repo = FakeParserRuleRepository()

    private fun rule(
        id: String,
        packageName: String,
        isActive: Boolean = true,
    ) = testRule(
        id = id,
        packageName = packageName,
        bankName = packageName,
        regexPattern = "(?<amount>\\d+)",
        isActive = isActive,
    )

    @Test
    fun `bank count follows rules`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = true, isDebuggable = true)
            runCurrent()
            assertEquals(0, vm.state.value.bankCount)

            repo.rules.value = listOf(rule("1", "a"), rule("2", "a"), rule("3", "b"), rule("4", "c", isActive = false))
            runCurrent()

            assertEquals(2, vm.state.value.bankCount)
        }

    @Test
    fun `resumed updates access`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = true, isDebuggable = true)

            vm.onIntent(SettingsIntent.Resumed(isGranted = true))
            assertTrue(vm.state.value.isAccessEnabled)

            vm.onIntent(SettingsIntent.Resumed(isGranted = false))
            assertFalse(vm.state.value.isAccessEnabled)
        }

    @Test
    fun `capture click emits OpenCaptureSettings`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = true, isDebuggable = true)

            vm.effects.test {
                vm.onIntent(SettingsIntent.CaptureClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenCaptureSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `accounts click emits OpenAccountsSettings`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = true, isDebuggable = true)

            vm.effects.test {
                vm.onIntent(SettingsIntent.AccountsClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenAccountsSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `logs row hidden when tools are off`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = false, isDebuggable = true)

            assertFalse(vm.state.value.isToolsEnabled)
        }

    @Test
    fun `logs click emits OpenNotificationLogs`() =
        runTest(mainRule.dispatcher) {
            val vm = SettingsViewModel(ObserveParserRulesUseCase(repo), isToolsEnabled = true, isDebuggable = true)

            assertTrue(vm.state.value.isToolsEnabled)
            vm.effects.test {
                vm.onIntent(SettingsIntent.LogsClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenNotificationLogs, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
