package io.github.toanhp123.finote.ui.setup

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.setup.repository.SetupRepository
import io.github.toanhp123.finote.domain.setup.usecase.CompleteSetupUseCase
import io.github.toanhp123.finote.ui.common.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private class FakeSetupRepository : SetupRepository {
        var doneCalls = 0
            private set

        override fun isSetupDone(): Boolean = doneCalls > 0

        override fun markSetupDone() {
            doneCalls++
        }
    }

    private val repo = FakeSetupRepository()

    private fun viewModel() = SetupViewModel(CompleteSetupUseCase(repo), isDebuggable = true)

    @Test
    fun `first resume without access keeps intro`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.onIntent(SetupIntent.Resumed(isGranted = false))

            assertEquals(SetupState(), vm.state.value)
            assertFalse(repo.isSetupDone())
        }

    @Test
    fun `first resume with access grants and finishes`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(SetupIntent.Resumed(isGranted = true))
                runCurrent()

                assertEquals(SetupStep.GRANTED, vm.state.value.step)
                assertTrue(repo.isSetupDone())
                expectNoEvents()
                advanceTimeBy(GRANTED_HOLD_MS)
                runCurrent()
                assertEquals(SetupEffect.Finished, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `second resume while granted does not finish twice`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(SetupIntent.Resumed(isGranted = true))
                vm.onIntent(SetupIntent.Resumed(isGranted = true))
                advanceTimeBy(GRANTED_HOLD_MS)
                runCurrent()

                assertEquals(SetupEffect.Finished, awaitItem())
                expectNoEvents()
                assertEquals(1, repo.doneCalls)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `enable opens settings`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(SetupIntent.EnableClicked)
                runCurrent()

                assertEquals(SetupEffect.OpenListenerSettings, awaitItem())
                assertTrue(vm.state.value.hasLaunchedSettings)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `resume after settings without access shows retry`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.onIntent(SetupIntent.EnableClicked)
            vm.onIntent(SetupIntent.Resumed(isGranted = false))

            assertEquals(SetupStep.RETRY, vm.state.value.step)
            assertFalse(repo.isSetupDone())
        }

    @Test
    fun `later marks done and finishes`() =
        runTest(mainRule.dispatcher) {
            val vm = viewModel()

            vm.effects.test {
                vm.onIntent(SetupIntent.LaterClicked)
                runCurrent()

                assertEquals(SetupEffect.Finished, awaitItem())
                assertTrue(repo.isSetupDone())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
