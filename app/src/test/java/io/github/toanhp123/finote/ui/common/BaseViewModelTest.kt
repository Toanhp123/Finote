package io.github.toanhp123.finote.ui.common

import app.cash.turbine.test
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test
    fun `storage error calls onStorageError`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = true)

            viewModel.launchFailing(StorageException(IllegalStateException()))
            runCurrent()

            assertEquals(listOf("storage"), viewModel.calls)
        }

    @Test
    fun `unexpected error calls onUnexpected when not debuggable`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)

            viewModel.launchFailing(IllegalStateException())
            runCurrent()

            assertEquals(listOf("unexpected"), viewModel.calls)
        }

    @Test
    fun `unexpected error defaults to onStorageError when not debuggable`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)

            viewModel.launchFailingWithDefault(IllegalStateException())
            runCurrent()

            assertEquals(listOf("storage"), viewModel.calls)
        }

    @Test
    fun `unexpected error is rethrown when debuggable`() {
        val viewModel = FakeViewModel(isDebuggable = true)
        val boom = IllegalStateException("boom")

        // The exception escapes the launched coroutine, so runTest reports it as a test failure.
        val failure =
            assertThrows(IllegalStateException::class.java) {
                runTest(mainRule.dispatcher) {
                    viewModel.launchFailing(boom)
                    runCurrent()
                }
            }

        assertSame(boom, failure)
        assertTrue(viewModel.calls.isEmpty())
    }

    @Test
    fun `cancellation is not caught`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)
            val gate = CompletableDeferred<Unit>()

            val job = viewModel.launchCatchingAndAwait(gate)
            runCurrent()
            job.cancel()
            runCurrent()

            assertTrue(job.isCancelled)
            assertTrue(viewModel.calls.isEmpty())
        }

    @Test
    fun `failing child inside coroutineScope reaches onStorageError`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)

            viewModel.launchFailingChild(StorageException(IllegalStateException()))
            runCurrent()

            assertEquals(listOf("storage"), viewModel.calls)
        }

    @Test
    fun `catchStorage rethrows a JVM Error in release`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)
            val oom = OutOfMemoryError()

            val failure = runCatching { viewModel.failingFlow(oom, fallback = -1).toList() }.exceptionOrNull()

            assertSame(oom, failure)
        }

    @Test
    fun `catchStorage emits fallback on StorageException`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = true)

            val items = viewModel.failingFlow(StorageException(IllegalStateException()), fallback = -1).toList()

            assertEquals(listOf(1, -1), items)
        }

    @Test
    fun `catchStorage rethrows unexpected when debuggable`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = true)
            val boom = IllegalStateException("boom")

            val failure = runCatching { viewModel.failingFlow(boom, fallback = -1).toList() }.exceptionOrNull()

            assertSame(boom, failure)
        }

    @Test
    fun `catchStorage calls onError for unexpected when not debuggable`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)

            val items = viewModel.failingFlow(IllegalStateException(), fallback = -1).toList()

            assertEquals(listOf(1, -1), items)
        }

    @Test
    fun `catchStorage lets CancellationException through`() =
        runTest(mainRule.dispatcher) {
            val viewModel = FakeViewModel(isDebuggable = false)
            val cancel = CancellationException("stop")

            val failure = runCatching { viewModel.failingFlow(cancel, fallback = -1).toList() }.exceptionOrNull()

            assertSame(cancel, failure)
        }
}

private sealed interface FakeIntent {
    data object Increment : FakeIntent

    data class Emit(
        val text: String,
    ) : FakeIntent
}

private class FakeViewModel(
    isDebuggable: Boolean = true,
) : BaseViewModel<Int, FakeIntent, String>(initial = 0, isDebuggable = isDebuggable) {
    val calls = mutableListOf<String>()

    fun launchFailing(error: Throwable) =
        launchCatching(
            onStorageError = { calls += "storage" },
            onUnexpected = { calls += "unexpected" },
        ) { throw error }

    fun launchFailingWithDefault(error: Throwable) =
        launchCatching(onStorageError = { calls += "storage" }) { throw error }

    fun launchFailingChild(error: Throwable) =
        launchCatching(
            onStorageError = { calls += "storage" },
            onUnexpected = { calls += "unexpected" },
        ) { coroutineScope { launch { throw error } } }

    fun launchCatchingAndAwait(gate: CompletableDeferred<Unit>) =
        launchCatching(
            onStorageError = { calls += "storage" },
            onUnexpected = { calls += "unexpected" },
        ) { gate.await() }

    fun failingFlow(
        error: Throwable,
        fallback: Int,
    ): Flow<Int> =
        flow {
            emit(1)
            throw error
        }.catchStorage { emit(fallback) }

    override fun onIntent(intent: FakeIntent) {
        when (intent) {
            FakeIntent.Increment -> setState { this + 1 }
            is FakeIntent.Emit -> sendEffect(intent.text)
        }
    }
}
