package io.github.toanhp123.finote.ui.common

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.toanhp123.finote.domain.common.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Base for MVI screens: [state] holds what to draw, [effects] carries one-time commands.
 *
 * Effects use a [Channel] because a screen has one consumer, an effect sent while no collector
 * is active (for example during rotation) waits in the buffer, and each effect is delivered once.
 *
 * [isDebuggable] decides what an unexpected error does: a debug build crashes so the bug shows,
 * a release build shows the user the same error as a storage failure.
 */
abstract class BaseViewModel<S, I, E>(
    initial: S,
    private val isDebuggable: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)

    protected fun setState(reduce: S.() -> S) = _state.update(reduce)

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /**
     * Storage error → [onStorageError]. Other non-cancellation error → rethrow in debug, [onUnexpected] in release.
     * A child that fails inside [block] is covered only when [block] wraps it in `coroutineScope`.
     */
    protected fun launchCatching(
        onStorageError: () -> Unit,
        onUnexpected: () -> Unit = onStorageError,
        block: suspend () -> Unit,
    ): Job =
        viewModelScope.launch {
            try {
                // A debug build lets any other error escape: it crashes, so the bug shows.
                if (isDebuggable) block() else shieldUnexpected(onUnexpected, block)
            } catch (_: StorageException) {
                onStorageError()
            }
        }

    /** Same split for a flow; ends the flow like `catch`. [onError] may emit a fallback. */
    protected fun <T> Flow<T>.catchStorage(onError: suspend FlowCollector<T>.() -> Unit): Flow<T> =
        catch { cause ->
            // A JVM Error or a cancellation always passes; in debug only a storage failure is handled.
            val isPassing = cause !is Exception || cause is CancellationException
            val isUnexpectedInDebug = isDebuggable && cause !is StorageException
            if (isPassing || isUnexpectedInDebug) throw cause
            onError()
        }

    private suspend fun shieldUnexpected(
        onUnexpected: () -> Unit,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: StorageException) {
            throw error
        } catch (_: Exception) {
            onUnexpected()
        }
    }
}
