package io.github.toanhp123.finote.ui.common

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BaseViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun `intent updates state through setState`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel()

            viewModel.state.test {
                assertEquals(0, awaitItem())
                viewModel.onIntent(FakeIntent.Increment)
                assertEquals(1, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `effect sent before any collector is received by a later collector`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel()

            viewModel.onIntent(FakeIntent.Emit("early"))
            runCurrent()

            viewModel.effects.test {
                assertEquals("early", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `each effect is delivered once`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel()
            viewModel.onIntent(FakeIntent.Emit("once"))
            runCurrent()

            viewModel.effects.test {
                assertEquals("once", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            viewModel.effects.test {
                runCurrent()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private sealed interface FakeIntent {
    data object Increment : FakeIntent

    data class Emit(
        val text: String,
    ) : FakeIntent
}

private class FakeViewModel : BaseViewModel<Int, FakeIntent, String>(initial = 0) {
    override fun onIntent(intent: FakeIntent) {
        when (intent) {
            FakeIntent.Increment -> setState { this + 1 }
            is FakeIntent.Emit -> sendEffect(intent.text)
        }
    }
}
