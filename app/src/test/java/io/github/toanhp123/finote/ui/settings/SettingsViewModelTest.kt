package io.github.toanhp123.finote.ui.settings

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.repository.ParserRuleRepository
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.FakeFailedInboxRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeInboxMarkerRepository
import io.github.toanhp123.finote.domain.notification.usecase.ObserveFailedInboxUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveInboxAlertsUseCase
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
    private val inbox = FakeFailedInboxRepository()
    private val markers = FakeInboxMarkerRepository()

    private fun viewModel(isToolsEnabled: Boolean = true) =
        SettingsViewModel(
            ObserveParserRulesUseCase(repo),
            ObserveInboxAlertsUseCase(ObserveFailedInboxUseCase(inbox), markers),
            isToolsEnabled = isToolsEnabled,
            isDebuggable = true,
        )

    private fun inboxItem(
        id: String,
        isHidden: Boolean = false,
    ) = FailedInboxItem(
        id = id,
        logIds = listOf(id),
        packageName = "a",
        title = "t",
        content = "c",
        postTime = 1L,
        receivedAt = 1L,
        reason = "no_rule_matched",
        parsed = null,
        isHidden = isHidden,
        isKeywordHidden = false,
    )

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
            val vm = viewModel(isToolsEnabled = true)
            runCurrent()
            assertEquals(0, vm.state.value.bankCount)

            repo.rules.value = listOf(rule("1", "a"), rule("2", "a"), rule("3", "b"), rule("4", "c", isActive = false))
            runCurrent()

            assertEquals(2, vm.state.value.bankCount)
        }

    @Test
    fun `capture click emits OpenCaptureSettings`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(isToolsEnabled = true)

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
            val vm = viewModel(isToolsEnabled = true)

            vm.effects.test {
                vm.onIntent(SettingsIntent.AccountsClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenAccountsSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `categories row opens the categories page`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(isToolsEnabled = false)

            vm.effects.test {
                vm.onIntent(SettingsIntent.CategoriesClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenCategoriesSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `logs row hidden when tools are off`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(isToolsEnabled = false)

            assertFalse(vm.state.value.isToolsEnabled)
        }

    @Test
    fun `logs click emits OpenNotificationLogs`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(isToolsEnabled = true)

            assertTrue(vm.state.value.isToolsEnabled)
            vm.effects.test {
                vm.onIntent(SettingsIntent.LogsClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenNotificationLogs, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `inbox row shows the shown count and the unseen dot`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            runCurrent()
            assertEquals(0, vm.state.value.inboxCount)
            assertFalse(vm.state.value.hasUnseenInbox)

            inbox.items.value = listOf(inboxItem("a"), inboxItem("b"), inboxItem("hidden", isHidden = true))
            runCurrent()
            assertEquals(2, vm.state.value.inboxCount)
            assertTrue(vm.state.value.hasUnseenInbox)

            markers.markSeen(setOf("a", "b"))
            runCurrent()
            assertEquals(2, vm.state.value.inboxCount)
            assertFalse(vm.state.value.hasUnseenInbox)
        }

    @Test
    fun `inbox click emits OpenFailedInbox in every build`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel(isToolsEnabled = false)

            vm.effects.test {
                vm.onIntent(SettingsIntent.InboxClicked)
                runCurrent()

                assertEquals(SettingsEffect.OpenFailedInbox, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
