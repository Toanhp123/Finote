package io.github.toanhp123.finote.ui.budgetedit

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.ui.budgetedit.component.BudgetForm
import io.github.toanhp123.finote.ui.budgetedit.component.DeleteBudgetDialog
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteSkeleton
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteBottomSheet
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme

/**
 * The sheet and the dialog of the budget editor. The first save asks for the notification permission here: the sheet
 * stays until the system dialog is answered, because the launcher lives in this composition.
 */
@Composable
fun BudgetEditRoute(
    viewModel: BudgetEditViewModel,
    onClose: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.onIntent(BudgetEditIntent.PermissionAnswered)
        }
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            BudgetEditEffect.AskNotificationPermission -> {
                // Below Android 13 there is no runtime permission to ask for.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onIntent(BudgetEditIntent.PermissionAnswered)
                }
            }
        }
    }
    FinoteBottomSheet(
        isVisible = state.isVisible,
        onDismiss = { viewModel.onIntent(BudgetEditIntent.Dismissed) },
        onDismissComplete = onClose,
    ) {
        BudgetEditScreen(state = state, onIntent = viewModel::onIntent)
    }
    DeleteBudgetDialog(
        isVisible = state.isDeleteDialogShown,
        onConfirm = { viewModel.onIntent(BudgetEditIntent.DeleteConfirmed) },
        onDismiss = { viewModel.onIntent(BudgetEditIntent.DeleteDismissed) },
    )
}

/** The content inside the sheet. It has no ViewModel, so previews and screenshot tests can draw it. */
@Composable
fun BudgetEditScreen(
    state: BudgetEditState,
    onIntent: (BudgetEditIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.isLoading) {
        // The stored values are not here yet: no create defaults flash before an edit.
        Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.md)) {
            repeat(SKELETON_ROWS) {
                FinoteSkeleton(Modifier.fillMaxWidth().height(FinoteTheme.sizes.touchTarget))
            }
        }
    } else {
        BudgetForm(state = state, onIntent = onIntent, modifier = modifier)
    }
}

private const val SKELETON_ROWS = 3
