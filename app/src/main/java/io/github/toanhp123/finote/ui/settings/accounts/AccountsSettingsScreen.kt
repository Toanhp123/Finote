package io.github.toanhp123.finote.ui.settings.accounts

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteIconButton
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.navigation.EditAccount
import io.github.toanhp123.finote.ui.settings.accounts.component.AccountGroupRow
import io.github.toanhp123.finote.ui.settings.accounts.component.AccountRow
import io.github.toanhp123.finote.ui.settings.accounts.component.ArchivedSection
import io.github.toanhp123.finote.ui.settings.accounts.component.ReorderList
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem
import kotlinx.coroutines.launch

@Composable
fun AccountsSettingsRoute(
    viewModel: AccountsSettingsViewModel,
    snackbarState: FinoteSnackbarState,
    onOpenAccount: (EditAccount) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val failed = stringResource(R.string.accounts_snack_failed)
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is AccountsSettingsEffect.OpenAccount -> onOpenAccount(EditAccount(effect.id, effect.mergeTargetId))
            AccountsSettingsEffect.ActionFailed -> scope.launch { snackbarState.show(failed) }
        }
    }
    // Back leaves the reorder mode first, without saving.
    BackHandler(enabled = state.reorder != null) { viewModel.onIntent(AccountsSettingsIntent.ReorderCancelled) }
    AccountsSettingsScreen(state, viewModel::onIntent, onClose, modifier)
}

@Composable
fun AccountsSettingsScreen(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.reorder
    Column(modifier = modifier.fillMaxSize()) {
        AccountsTopBar(isReorder = draft != null, canReorder = state.items.size > 1, onIntent, onBack)
        if (draft != null) {
            ReorderList(draft, onIntent, Modifier.weight(1f))
        } else {
            AccountList(state, onIntent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AccountsTopBar(
    isReorder: Boolean,
    canReorder: Boolean,
    onIntent: (AccountsSettingsIntent) -> Unit,
    onBack: () -> Unit,
) {
    if (isReorder) {
        FinoteTopBar(
            title = stringResource(R.string.accounts_reorder),
            actions = {
                FinoteIconButton(
                    iconRes = R.drawable.ic_close,
                    contentDescription = stringResource(R.string.accounts_reorder_cancel),
                    onClick = { onIntent(AccountsSettingsIntent.ReorderCancelled) },
                )
                FinoteIconButton(
                    iconRes = R.drawable.ic_check,
                    contentDescription = stringResource(R.string.accounts_reorder_done),
                    onClick = { onIntent(AccountsSettingsIntent.ReorderDone) },
                )
            },
        )
    } else {
        FinoteTopBar(
            title = stringResource(R.string.settings_accounts_title),
            onBack = onBack,
            actions = {
                if (canReorder) {
                    FinoteIconButton(
                        iconRes = R.drawable.ic_swap_vert,
                        contentDescription = stringResource(R.string.accounts_reorder),
                        onClick = { onIntent(AccountsSettingsIntent.ReorderClicked) },
                    )
                }
                FinoteIconButton(
                    iconRes = R.drawable.ic_add,
                    contentDescription = stringResource(R.string.accounts_add),
                    onClick = { onIntent(AccountsSettingsIntent.AddClicked) },
                )
            },
        )
    }
}

@Composable
private fun AccountList(
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = FinoteTheme.spacing
    val byId = remember(state.items) { state.items.flatMap { it.accounts }.associateBy { it.id } }
    // The partner of an account that carries a merge suggestion, or null.
    val partnerOf = { account: Account -> state.suggestions[account.id]?.let(byId::get) }
    Column(
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(start = spacing.lg, end = spacing.lg, bottom = spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.lg),
    ) {
        FinoteCard(modifier = Modifier.fillMaxWidth()) {
            state.items.forEach { item ->
                when (item) {
                    is AccountListItem.Single -> {
                        AccountRow(item.account, onIntent, partner = partnerOf(item.account))
                    }

                    is AccountListItem.Group -> {
                        GroupRows(item, state, onIntent, partnerOf)
                    }
                }
            }
        }
        if (state.archived.isNotEmpty()) {
            ArchivedSection(state.archived, state.isArchivedExpanded, onIntent)
        }
    }
}

/** The group row and, while the group is open, its members indented under it. */
@Composable
private fun GroupRows(
    group: AccountListItem.Group,
    state: AccountsSettingsState,
    onIntent: (AccountsSettingsIntent) -> Unit,
    partnerOf: (Account) -> Account?,
) {
    val isExpanded = group.packageName in state.expanded
    Column {
        AccountGroupRow(
            group = group,
            isExpanded = isExpanded,
            hasSuggestion = group.members.any { it.id in state.suggestions },
            onIntent = onIntent,
        )
        if (isExpanded) {
            group.members.forEach { member ->
                AccountRow(member, onIntent, Modifier.padding(start = FinoteTheme.spacing.lg), partnerOf(member))
            }
        }
    }
}
