package io.github.toanhp123.finote.ui.settings.capture

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.common.FakeTransactionRunner
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateCheck
import io.github.toanhp123.finote.domain.notification.model.RuleUpdateResult
import io.github.toanhp123.finote.domain.notification.model.UserIgnoreKeyword
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.CheckRulesNowUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DeleteAdKeywordUseCase
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeRuleUpdateScheduler
import io.github.toanhp123.finote.domain.notification.usecase.FakeRuleUpdateSettingsRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeSettleScheduler
import io.github.toanhp123.finote.domain.notification.usecase.FakeUserKeywordRepository
import io.github.toanhp123.finote.domain.notification.usecase.ObserveAdKeywordsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleCheckingUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveRuleUpdateStatusUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ReparseFailedLogsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.SetRuleAutoUpdateUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationCaptureSettingsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = FakeRuleUpdateSettingsRepository()
    private val rules = FakeParserRuleRepository()
    private val scheduler = FakeRuleUpdateScheduler()
    private val keywordRepo = FakeUserKeywordRepository()

    private fun viewModel(): NotificationCaptureSettingsViewModel {
        val reparse =
            ReparseFailedLogsUseCase(FakeNotificationCaptureRepository(), mainRule.dispatcher, FakeSettleScheduler())
        return NotificationCaptureSettingsViewModel(
            ObserveRuleUpdateStatusUseCase(settings),
            ObserveParserRulesUseCase(rules),
            ObserveRuleCheckingUseCase(scheduler),
            ObserveAdKeywordsUseCase(keywordRepo),
            CheckRulesNowUseCase(scheduler),
            SetRuleAutoUpdateUseCase(settings, scheduler),
            DeleteAdKeywordUseCase(keywordRepo, FakeTransactionRunner(), reparse),
            isDebuggable = true,
        )
    }

    private fun keyword(
        id: String,
        packageName: String,
    ) = UserIgnoreKeyword(id, packageName, "ưu đãi $id", "uu dai $id", createdAt = 1L)

    private fun rule(
        id: String,
        packageName: String,
    ) = testRule(
        id = id,
        packageName = packageName,
        bankName = packageName,
        regexPattern = "(?<amount>[0-9]+)",
    )

    @Test
    fun `state combines prefs, checking and banks`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            runCurrent()
            val check = RuleUpdateCheck(checkedAt = 1_000L, result = RuleUpdateResult.Updated(2))

            vm.onIntent(NotificationCaptureSettingsIntent.AutoUpdateToggled(isEnabled = false))
            settings.lastCheck.value = check
            scheduler.isChecking.value = true
            rules.stored.value = listOf(rule("1", "a"), rule("2", "a"), rule("3", "b"))
            runCurrent()

            val state = vm.state.value
            assertFalse(state.isAutoUpdateEnabled)
            assertTrue(state.isChecking)
            assertEquals(check, state.lastCheck)
            assertEquals(listOf(2, 1), state.banks.map { it.activeRuleCount })
            assertFalse(scheduler.isPeriodicEnabled)
        }

    @Test
    fun `check now calls scheduler and ignores taps while checking`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            runCurrent()

            vm.onIntent(NotificationCaptureSettingsIntent.CheckNowClicked)
            assertEquals(1, scheduler.checkNowCount)

            scheduler.isChecking.value = true
            runCurrent()
            vm.onIntent(NotificationCaptureSettingsIntent.CheckNowClicked)
            assertEquals(1, scheduler.checkNowCount)
        }

    @Test
    fun `capture settings lists and deletes keywords`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()
            keywordRepo.keywords.value = listOf(keyword("1", "a"), keyword("2", "a"), keyword("3", "b"))
            runCurrent()

            assertEquals(
                listOf("1", "2"),
                vm.state.value.adKeywords["a"]
                    ?.map { it.id },
            )
            assertEquals(
                listOf("3"),
                vm.state.value.adKeywords["b"]
                    ?.map { it.id },
            )

            vm.onIntent(NotificationCaptureSettingsIntent.AdKeywordDeleteClicked("2"))
            runCurrent()

            assertEquals(listOf("2"), keywordRepo.removedIds)
            assertEquals(
                listOf("1"),
                vm.state.value.adKeywords["a"]
                    ?.map { it.id },
            )
        }

    @Test
    fun `access click emits OpenListenerSettings`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(NotificationCaptureSettingsIntent.AccessClicked)
                runCurrent()

                assertEquals(NotificationCaptureSettingsEffect.OpenListenerSettings, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `back emits Close`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(NotificationCaptureSettingsIntent.BackClicked)
                runCurrent()

                assertEquals(NotificationCaptureSettingsEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
