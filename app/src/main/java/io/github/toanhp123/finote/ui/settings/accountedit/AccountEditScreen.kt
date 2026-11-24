package io.github.toanhp123.finote.ui.settings.accountedit

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import io.github.toanhp123.finote.domain.account.model.defaultKind
import io.github.toanhp123.finote.ui.common.ObserveEffects
import io.github.toanhp123.finote.ui.common.component.control.FinoteButton
import io.github.toanhp123.finote.ui.common.component.control.FinoteButtonStyle
import io.github.toanhp123.finote.ui.common.component.display.FinoteCard
import io.github.toanhp123.finote.ui.common.component.display.FinoteListRow
import io.github.toanhp123.finote.ui.common.component.finance.MoneyText
import io.github.toanhp123.finote.ui.common.component.layout.FinoteTopBar
import io.github.toanhp123.finote.ui.common.component.overlay.FinoteSnackbarState
import io.github.toanhp123.finote.ui.common.format.MoneyKind
import io.github.toanhp123.finote.ui.common.format.displayName
import io.github.toanhp123.finote.ui.common.rememberLastNonNull
import io.github.toanhp123.finote.ui.common.theme.FinoteTheme
import io.github.toanhp123.finote.ui.settings.accountedit.component.AccountFormSection
import io.github.toanhp123.finote.ui.settings.accountedit.component.AddIdentifierDialog
import io.github.toanhp123.finote.ui.settings.accountedit.component.DeleteAccountDialog
import io.github.toanhp123.finote.ui.settings.accountedit.component.IdentifierSection
import io.github.toanhp123.finote.ui.settings.accountedit.component.IgnoreDialog
import io.github.toanhp123.finote.ui.settings.accountedit.component.MergeDialog
import io.github.toanhp123.finote.ui.settings.accountedit.component.MergeTargetPickerSheet
import io.github.toanhp123.finote.ui.settings.accountedit.component.RemoveIdentifierDialog
import io.github.toanhp123.finote.ui.settings.accountedit.logic.messageRes
import io.github.toanhp123.finote.ui.transactions.component.ReconcileSheet
import kotlinx.coroutines.launch

/**
 * [onClose] runs when the screen should leave. A non-null message says why (the account was deleted, merged or gone):
 * the host shows it, because the message must outlive this screen.
 */
@Composable
fun AccountEditRoute(
    viewModel: AccountEditViewModel,
    snackbarState: FinoteSnackbarState,
    onClose: (message: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    ObserveEffects(viewModel.effects) { effect ->
        when (effect) {
            is AccountEditEffect.Close -> {
                onClose(effect.reason?.let { resources.getString(it.messageRes()) })
            }

            is AccountEditEffect.ShowError -> {
                scope.launch { snackbarState.show(resources.message(effect.error)) }
            }

            is AccountEditEffect.ShowReconcileFailed -> {
                scope.launch { snackbarState.show(resources.getString(R.string.reconcile_error)) }
            }

            AccountEditEffect.ShowNoMergeTarget -> {
                scope.launch { snackbarState.show(resources.getString(R.string.account_edit_no_merge_target)) }
            }

            AccountEditEffect.ShowStorageFailed -> {
                scope.launch { snackbarState.show(resources.getString(R.string.accounts_snack_failed)) }
            }
        }
    }
    AccountEditScreen(state, viewModel::onIntent, onBack = { onClose(null) }, modifier)
}

@Composable
fun AccountEditScreen(
    state: AccountEditState,
    onIntent: (AccountEditIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val isCreate = state.stored == null && draft != null
    // The overlays are drawn after the body in the same Box, so an inline overlay (preview mode) covers the top bar.
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            FinoteTopBar(
                title =
                    stringResource(if (isCreate) R.string.account_edit_title_new else R.string.account_edit_title_edit),
                onBack = onBack,
            )
            if (draft != null) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = FinoteTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xl),
                ) {
                    AccountFormSection(
                        draft = draft,
                        stored = state.stored,
                        bankApps = state.bankApps,
                        onDraftChange = { onIntent(AccountEditIntent.DraftChanged(it)) },
                    )
                    state.stored?.let { EditSections(state, it, onIntent) }
                }
                FinoteButton(
                    text = stringResource(R.string.cash_entry_save),
                    onClick = { onIntent(AccountEditIntent.SaveClicked) },
                    modifier = Modifier.fillMaxWidth().padding(FinoteTheme.spacing.lg),
                    enabled = draft.name.isNotBlank(),
                    isLoading = state.isSaving,
                )
            }
        }
        AccountEditOverlays(state, onIntent)
    }
}

/** What an existing account adds to the form: balance, star, masked numbers and the actions on the account. */
@Composable
private fun EditSections(
    state: AccountEditState,
    stored: Account,
    onIntent: (AccountEditIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.xl)) {
        BalanceAndStar(stored, onIntent)
        if (stored.type != AccountType.CASH) {
            IdentifierSection(stored = stored, identifiers = state.identifiers, onIntent = onIntent)
        }
        AccountActions(stored, onIntent)
    }
}

@Composable
private fun BalanceAndStar(
    stored: Account,
    onIntent: (AccountEditIntent) -> Unit,
) {
    // A provisional start is edited in the form; an archived or ignored account must be restored first.
    val canCorrect = stored.isInitialized && stored.isUsable
    if (!stored.isPrimary && !stored.isUsable) return
    FinoteCard(modifier = Modifier.fillMaxWidth()) {
        if (canCorrect) {
            FinoteListRow(
                title = stringResource(R.string.account_edit_correct_balance),
                trailing = { MoneyText(amount = stored.currentBalance, kind = MoneyKind.Neutral) },
                onClick = { onIntent(AccountEditIntent.ReconcileClicked) },
            )
        }
        // Only a usable account can take the star, and no use case removes it: it moves when another account takes it.
        if (stored.isPrimary) {
            FinoteListRow(title = stringResource(R.string.account_edit_primary), leading = { StarIcon() })
        } else if (stored.isUsable) {
            FinoteListRow(
                title = stringResource(R.string.account_edit_star),
                leading = { StarIcon() },
                onClick = { onIntent(AccountEditIntent.StarToggled) },
            )
        }
    }
}

@Composable
private fun StarIcon() {
    Icon(
        painter = painterResource(R.drawable.ic_stars),
        contentDescription = null,
        modifier = Modifier.size(FinoteTheme.sizes.iconMd),
        tint = FinoteTheme.scheme.primary,
    )
}

@Composable
private fun AccountActions(
    stored: Account,
    onIntent: (AccountEditIntent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(FinoteTheme.spacing.lg)) {
        FinoteCard(modifier = Modifier.fillMaxWidth()) {
            // Only an account with a bank key can have a twin to merge with or notifications to ignore.
            if (stored.bankPackageName != null) {
                FinoteListRow(
                    title = stringResource(R.string.accounts_merge_into),
                    onClick = { onIntent(AccountEditIntent.MergeClicked) },
                )
                if (stored.isIgnored) {
                    FinoteListRow(
                        title = stringResource(R.string.accounts_follow_again),
                        subtitle = stringResource(R.string.accounts_ignored_label),
                        onClick = { onIntent(AccountEditIntent.FollowAgainClicked) },
                    )
                } else {
                    FinoteListRow(
                        title = stringResource(R.string.accounts_ignore_action),
                        onClick = { onIntent(AccountEditIntent.IgnoreClicked) },
                    )
                }
            }
            FinoteListRow(
                title =
                    stringResource(
                        if (stored.isArchived) R.string.account_edit_unarchive else R.string.account_edit_archive,
                    ),
                subtitle = if (stored.isArchived) stringResource(R.string.account_edit_archived_hint) else null,
                onClick = { onIntent(AccountEditIntent.ArchiveToggled) },
            )
        }
        if (stored.id != SYSTEM_CASH_ACCOUNT_ID) {
            FinoteButton(
                text = stringResource(R.string.account_edit_delete),
                onClick = { onIntent(AccountEditIntent.DeleteClicked) },
                modifier = Modifier.fillMaxWidth(),
                style = FinoteButtonStyle.Danger,
            )
        }
    }
}

@Composable
private fun AccountEditOverlays(
    state: AccountEditState,
    onIntent: (AccountEditIntent) -> Unit,
) {
    EditDialogs(state, onIntent)
    MergeTargetPickerSheet(picker = state.dialog as? AccountEditDialog.MergeTargetPicker, onIntent = onIntent)
    ReconcileSheet(
        account = state.stored.takeIf { state.dialog == AccountEditDialog.Reconcile },
        onSubmit = { onIntent(AccountEditIntent.ReconcileSubmitted(it)) },
        onDismiss = { onIntent(AccountEditIntent.DialogDismissed) },
    )
}

/** Keeps the last dialog while it fades out, so the text does not blank. The two sheets draw themselves. */
@Composable
private fun EditDialogs(
    state: AccountEditState,
    onIntent: (AccountEditIntent) -> Unit,
) {
    val isVisible = state.dialog != null
    when (val shown = rememberLastNonNull(state.dialog)) {
        is AccountEditDialog.Merge -> MergeDialog(shown, isVisible, onIntent)
        is AccountEditDialog.Ignore -> IgnoreDialog(shown, isVisible, onIntent)
        AccountEditDialog.Delete -> DeleteAccountDialog(isVisible, onIntent)
        AccountEditDialog.AddIdentifier -> AddIdentifierDialog(isVisible, state.stored?.type.defaultKind(), onIntent)
        is AccountEditDialog.RemoveLastIdentifier -> RemoveIdentifierDialog(isVisible, onIntent)
        is AccountEditDialog.MergeTargetPicker, AccountEditDialog.Reconcile, null -> Unit
    }
}

@StringRes
private fun CloseReason.messageRes(): Int =
    when (this) {
        CloseReason.DELETED -> R.string.account_snack_deleted
        CloseReason.MERGED -> R.string.accounts_snack_merged
        CloseReason.GONE -> R.string.account_snack_gone
    }

/** The message of a refused change. A taken number names its holder, and the rule that blocks a merge, if any. */
private fun Resources.message(error: AccountError): String =
    if (error is AccountError.IdentifierTaken) {
        val taken = getString(error.messageRes(), this.displayName(error.holder.name, error.holder.nameKey))
        error.mergeRejection?.let { "$taken ${getString(it.messageRes())}" } ?: taken
    } else {
        getString(error.messageRes())
    }
