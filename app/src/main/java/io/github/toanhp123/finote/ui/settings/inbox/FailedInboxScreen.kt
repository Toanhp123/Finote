package io.github.toanhp123.finote.ui.settings.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.display.FinoteEmptyState
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.inbox.component.AdKeywordSheet
import io.github.toanhp123.finote.ui.settings.inbox.component.HiddenSection
import io.github.toanhp123.finote.ui.settings.inbox.component.InboxItemRow
import kotlinx.coroutines.launch

/** [onOpenBooking] gets the item id; the host decides what opens. The ad sheet and its Undo live here. */
@Composable
fun FailedInboxRoute(
    viewModel: FailedInboxViewModel,
    snackbarState: FinoteSnackbarState,
    onOpenBooking: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val handled = stringResource(R.string.inbox_snack_handled)
    val failed = stringResource(R.string.inbox_snack_failed)
    val adLearned = stringResource(R.string.inbox_snack_ad_learned)
    val undo = stringResource(R.string.inbox_snack_undo)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is FailedInboxEffect.OpenBooking -> {
                onOpenBooking(effect.itemId)
            }

            is FailedInboxEffect.ShowAdLearned -> {
                scope.launch {
                    if (snackbarState.show(adLearned, undo)) {
                        viewModel.onIntent(FailedInboxIntent.AdUndoClicked(effect.keywordId))
                    }
                }
            }

            FailedInboxEffect.ShowAlreadyHandled -> {
                scope.launch { snackbarState.show(handled) }
            }

            FailedInboxEffect.ShowActionFailed -> {
                scope.launch { snackbarState.show(failed) }
            }

            FailedInboxEffect.Close -> {
                onClose()
            }
        }
    }
    FailedInboxScreen(state, viewModel::onIntent, modifier)
}

@Composable
fun FailedInboxScreen(
    state: FailedInboxState,
    onIntent: (FailedInboxIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        FinoteTopBar(
            title = stringResource(R.string.settings_inbox_title),
            onBack = { onIntent(FailedInboxIntent.BackClicked) },
        )
        if (state.isLoaded && state.items.isEmpty()) {
            FinoteEmptyState(
                iconRes = R.drawable.ic_inbox,
                title = stringResource(R.string.inbox_empty_title),
                message = stringResource(R.string.inbox_empty_message),
            )
        } else if (state.isLoaded) {
            InboxList(state, onIntent)
        }
    }
    AdKeywordSheet(state.adSheet, onIntent)
}

@Composable
private fun InboxList(
    state: FailedInboxState,
    onIntent: (FailedInboxIntent) -> Unit,
) {
    val hidden = state.hidden
    LazyColumn(
        contentPadding = PaddingValues(horizontal = FinoteTheme.spacing.lg, vertical = FinoteTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.sm),
    ) {
        items(state.shown, key = { it.id }) { item -> ItemRow(item, state, onIntent) }
        if (hidden.isNotEmpty()) {
            item(key = HIDDEN_HEADER_KEY) {
                HiddenSection(
                    count = hidden.size,
                    isExpanded = state.isHiddenExpanded,
                    onToggle = { onIntent(FailedInboxIntent.HiddenToggled) },
                )
            }
            if (state.isHiddenExpanded) {
                items(hidden, key = { it.id }) { item -> ItemRow(item, state, onIntent) }
            }
        }
    }
}

@Composable
private fun ItemRow(
    item: FailedInboxItem,
    state: FailedInboxState,
    onIntent: (FailedInboxIntent) -> Unit,
) {
    InboxItemRow(
        item = item,
        bankName = state.bankNames[item.packageName] ?: item.packageName,
        onBook = { onIntent(FailedInboxIntent.ItemClicked(item.id)) },
        onDismiss = { onIntent(FailedInboxIntent.DismissClicked(item.id)) },
        onAd = { onIntent(FailedInboxIntent.AdClicked(item.id)) },
        onRestore = { onIntent(FailedInboxIntent.RestoreClicked(item.id)) },
    )
}

private const val HIDDEN_HEADER_KEY = "hidden-header"
