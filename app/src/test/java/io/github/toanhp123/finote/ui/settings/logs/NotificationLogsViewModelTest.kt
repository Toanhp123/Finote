package io.github.toanhp123.finote.ui.settings.logs

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.notification.testLog
import io.github.toanhp123.finote.domain.notification.testRule
import io.github.toanhp123.finote.domain.notification.usecase.FakeNotificationCaptureRepository
import io.github.toanhp123.finote.domain.notification.usecase.FakeParserRuleRepository
import io.github.toanhp123.finote.domain.notification.usecase.ObserveNotificationLogsUseCase
import io.github.toanhp123.finote.domain.notification.usecase.ObserveParserRulesUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationLogsViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val captureRepo = FakeNotificationCaptureRepository()
    private val ruleRepo = FakeParserRuleRepository()

    private fun viewModel() =
        NotificationLogsViewModel(
            ObserveNotificationLogsUseCase(captureRepo),
            ObserveParserRulesUseCase(ruleRepo),
            isDebuggable = true,
        )

    @Test
    fun `state follows the log flow and names banks from rules`() =
        runTest(mainRule.dispatcher) {
            ruleRepo.stored.value = listOf(testRule(packageName = "com.VCB", bankName = "Vietcombank"))
            captureRepo.recentLogs.value = listOf(testLog(id = "a", packageName = "com.VCB"))
            val vm = viewModel()
            runCurrent()

            assertEquals(
                listOf("a"),
                vm.state.value.logs
                    .map { it.id },
            )
            assertEquals("Vietcombank", vm.state.value.bankNames["com.VCB"])
        }

    @Test
    fun `log click opens the sheet and dismiss closes it`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a"))
            val vm = viewModel()
            runCurrent()

            vm.onIntent(NotificationLogsIntent.LogClicked("a"))
            assertEquals(
                "a",
                vm.state.value.selected
                    ?.id,
            )

            vm.onIntent(NotificationLogsIntent.SheetDismissed)
            assertNull(vm.state.value.selected)
        }

    @Test
    fun `log click on an unknown id is ignored`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a"))
            val vm = viewModel()
            runCurrent()

            vm.onIntent(NotificationLogsIntent.LogClicked("gone"))

            assertNull(vm.state.value.selected)
        }

    @Test
    fun `selected follows the new list and clears when its log is gone`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a", title = "old"))
            val vm = viewModel()
            runCurrent()
            vm.onIntent(NotificationLogsIntent.LogClicked("a"))

            captureRepo.recentLogs.value = listOf(testLog(id = "a", title = "new"))
            runCurrent()
            assertEquals(
                "new",
                vm.state.value.selected
                    ?.title,
            )

            captureRepo.recentLogs.value = listOf(testLog(id = "b"))
            runCurrent()
            assertNull(vm.state.value.selected)
        }

    @Test
    fun `copy emits masked json`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a", content = "SD 0123"))
            val vm = viewModel()
            runCurrent()
            vm.onIntent(NotificationLogsIntent.LogClicked("a"))

            vm.effects.test {
                vm.onIntent(NotificationLogsIntent.CopyClicked)
                runCurrent()

                val effect = awaitItem() as NotificationLogsEffect.CopySample
                assertTrue(effect.json.contains("SD 1111"))
                assertFalse(effect.json.contains("0123"))
                assertEquals("CopySample", effect.toString())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `copy is off for a blank log`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a", title = "", content = ""))
            val vm = viewModel()
            runCurrent()
            vm.onIntent(NotificationLogsIntent.LogClicked("a"))

            assertFalse(vm.state.value.isCopyEnabled)
            vm.effects.test {
                vm.onIntent(NotificationLogsIntent.CopyClicked)
                runCurrent()

                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `back emits close`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(NotificationLogsIntent.BackClicked)
                runCurrent()

                assertEquals(NotificationLogsEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `state toString hides text`() =
        runTest(mainRule.dispatcher) {
            captureRepo.recentLogs.value = listOf(testLog(id = "a", title = "SECRET-T", content = "SECRET-C"))
            val vm = viewModel()
            runCurrent()
            vm.onIntent(NotificationLogsIntent.LogClicked("a"))

            assertFalse(
                vm.state.value
                    .toString()
                    .contains("SECRET"),
            )
        }
}
