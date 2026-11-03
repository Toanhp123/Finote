package io.github.toanhp123.finote.ui.common.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Shows one snackbar at a time in the [FinoteScaffold] it is passed to. Later messages wait their turn. */
class FinoteSnackbarState internal constructor(
    internal val host: SnackbarHostState,
) {
    /** Suspends until the snackbar goes away. Returns true when the user pressed the action. */
    suspend fun show(
        message: String,
        actionLabel: String? = null,
    ): Boolean = host.showSnackbar(message = message, actionLabel = actionLabel) == SnackbarResult.ActionPerformed
}

@Composable
fun rememberFinoteSnackbarState(): FinoteSnackbarState = remember { FinoteSnackbarState(SnackbarHostState()) }
