package io.github.toanhp123.finote.ui.settings.accountedit

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.usecase.AddAccountIdentifierUseCase
import io.github.toanhp123.finote.domain.account.usecase.ArchiveAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountMergeMovesUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountTrashableTransactionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.CreateAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.DeleteAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.GetMergeDroppedStartUseCase
import io.github.toanhp123.finote.domain.account.usecase.MergeAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountIdentifiersUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBankAppsUseCase
import io.github.toanhp123.finote.domain.account.usecase.RemoveAccountIdentifierUseCase
import io.github.toanhp123.finote.domain.account.usecase.SaveAccountUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetAccountIgnoredUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetPrimaryAccountUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.usecase.ReconcileBalanceUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.accountedit.logic.AccountDraft
import io.github.toanhp123.finote.ui.settings.accountedit.logic.NewAccountDraft
import io.github.toanhp123.finote.ui.settings.accountedit.logic.isDirtyAgainst
import io.github.toanhp123.finote.ui.settings.accountedit.logic.isLastIdentifier
import io.github.toanhp123.finote.ui.settings.accountedit.logic.lockedBy
import io.github.toanhp123.finote.ui.settings.accountedit.logic.mergeCandidates
import io.github.toanhp123.finote.ui.settings.accountedit.logic.startToSave
import io.github.toanhp123.finote.ui.settings.accountedit.logic.toAccount
import io.github.toanhp123.finote.ui.settings.accountedit.logic.toDraft
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Create mode ([id] null) edits a draft only. Edit mode fills the draft once from the stored account, then lets the
 * database update [AccountEditState.stored] and the identifiers without touching what the user typed.
 */
@HiltViewModel(assistedFactory = AccountEditViewModel.Factory::class)
class AccountEditViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @AssistedInject
    constructor(
        @Assisted("id") private val id: String?,
        // Not null: open the merge dialog with this account as target once the account loads (a list suggestion).
        @Assisted("mergeTarget") mergeTargetId: String?,
        observeAccounts: ObserveAccountsUseCase,
        observeIdentifiers: ObserveAccountIdentifiersUseCase,
        observeBankApps: ObserveBankAppsUseCase,
        private val createAccount: CreateAccountUseCase,
        private val saveAccount: SaveAccountUseCase,
        private val setPrimary: SetPrimaryAccountUseCase,
        private val archiveAccount: ArchiveAccountUseCase,
        private val deleteAccount: DeleteAccountUseCase,
        private val setIgnored: SetAccountIgnoredUseCase,
        private val addIdentifier: AddAccountIdentifierUseCase,
        private val removeIdentifier: RemoveAccountIdentifierUseCase,
        private val mergeAccounts: MergeAccountsUseCase,
        private val countMergeMoves: CountMergeMovesUseCase,
        private val getDroppedStart: GetMergeDroppedStartUseCase,
        private val countTrashable: CountTrashableTransactionsUseCase,
        private val reconcile: ReconcileBalanceUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<AccountEditState, AccountEditIntent, AccountEditEffect>(
            AccountEditState(draft = if (id == null) NewAccountDraft else null),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("id") id: String?,
                @Assisted("mergeTarget") mergeTargetId: String?,
            ): AccountEditViewModel
        }

        private var allAccounts: List<Account> = emptyList()
        private var pendingMergeTarget: String? = mergeTargetId

        // Set before an own delete or merge, so the account vanishing from the database closes with this reason.
        private var pendingClose: CloseReason? = null
        private var isClosed = false

        init {
            viewModelScope.launch {
                observeBankApps()
                    .catchStorage {
                        // The bank list stays as it was. Nothing is logged.
                    }.collect { apps -> setState { copy(bankApps = apps) } }
            }
            if (id != null) {
                viewModelScope.launch {
                    combine(observeAccounts(), observeIdentifiers(id)) { list, masks -> list to masks }
                        .catchStorage {
                            sendStorageFailed()
                            // Nothing was ever shown, so there is nothing to keep.
                            if (state.value.draft == null) close(CloseReason.GONE)
                        }.collect { (list, masks) -> onAccounts(list, masks) }
                }
            }
        }

        override fun onIntent(intent: AccountEditIntent) {
            when (intent) {
                is AccountEditIntent.DraftChanged -> changeDraft(intent.draft)
                AccountEditIntent.DialogDismissed -> setState { copy(dialog = null) }
                AccountEditIntent.SaveClicked -> save()
                AccountEditIntent.StarToggled -> toggleStar()
                AccountEditIntent.ArchiveToggled -> toggleArchive()
                AccountEditIntent.FollowAgainClicked -> followAgain()
                AccountEditIntent.ReconcileClicked -> openDialog(AccountEditDialog.Reconcile)
                is AccountEditIntent.ReconcileSubmitted -> reconcileTo(intent.actual)
                AccountEditIntent.AddIdentifierClicked -> openDialog(AccountEditDialog.AddIdentifier)
                is AccountEditIntent.AddIdentifierSubmitted -> addMask(intent.kind, intent.mask)
                is AccountEditIntent.RemoveIdentifierClicked -> removeMask(intent.identifier)
                AccountEditIntent.RemoveIdentifierConfirmed -> confirmRemoveMask()
                AccountEditIntent.IgnoreClicked -> askIgnore()
                is AccountEditIntent.TrashToggled -> toggleTrash(intent.isChecked)
                AccountEditIntent.IgnoreConfirmed -> confirmIgnore()
                AccountEditIntent.DeleteClicked -> openDialog(AccountEditDialog.Delete)
                AccountEditIntent.DeleteConfirmed -> confirmDelete()
                AccountEditIntent.MergeClicked -> askMergeTarget()
                is AccountEditIntent.MergeTargetPicked -> askMerge(intent.targetId)
                AccountEditIntent.MergeConfirmed -> confirmMerge()
            }
        }

        private fun onAccounts(
            list: List<Account>,
            masks: List<AccountIdentifier>,
        ) {
            allAccounts = list
            val account = list.firstOrNull { it.id == id && it.deletedAt == null }
            if (account == null) {
                setState { copy(stored = null, dialog = null) }
                close(pendingClose ?: CloseReason.GONE)
                return
            }
            // The draft is filled once; later emissions leave what the user typed alone.
            setState {
                copy(
                    stored = account,
                    identifiers = masks,
                    draft = draft ?: account.toDraft(),
                    loadedStart = if (draft == null) account.initialBalance else loadedStart,
                )
            }
            pendingMergeTarget?.let { targetId ->
                pendingMergeTarget = null
                allAccounts.firstOrNull { it.id == targetId }?.let { target ->
                    launchCatching(onStorageError = ::sendStorageFailed) { openMerge(account, target) }
                }
            }
        }

        /** Runs [block] with the stored account; does nothing once the account is gone. */
        private fun withAccount(block: (Account) -> Unit) {
            state.value.stored?.let(block)
        }

        private fun openDialog(dialog: AccountEditDialog) = withAccount { setState { copy(dialog = dialog) } }

        private fun changeDraft(next: AccountDraft) =
            setState { copy(draft = draft?.let { next.lockedBy(stored, it) }) }

        private fun save() {
            val draft = state.value.draft ?: return
            val stored = state.value.stored
            // An edit whose account is gone has nothing to write.
            if (state.value.isSaving || (id != null && stored == null)) return
            launchCatching(onStorageError = ::sendStorageFailed) {
                val isSaved = if (stored == null) create(draft) else saveEdit(stored, draft)
                if (isSaved) sendEffect(AccountEditEffect.Close(null))
            }
        }

        // An empty start means "unknown" only for a bank app account; the use case decides.
        private suspend fun create(draft: AccountDraft): Boolean {
            val account = draft.toAccount(base = null, id = UUID.randomUUID().toString())
            return saving { createAccount(account, isStartKnown = draft.startBalance != null) }
        }

        private suspend fun saveEdit(
            stored: Account,
            draft: AccountDraft,
        ): Boolean =
            saving {
                saveAccount(draft.toAccount(stored, stored.id), draft.startToSave(stored, state.value.loadedStart))
            }

        // No use case unstars: the star moves when another account takes it.
        private fun toggleStar() = withAccount { if (!it.isPrimary) act { setPrimary(it.id) } }

        private fun toggleArchive() = withAccount { act { archiveAccount(it.id, archived = !it.isArchived) } }

        private fun followAgain() =
            withAccount { act { setIgnored(it.id, ignored = false, shouldTrashOldTransactions = false) } }

        private fun reconcileTo(actual: Long) =
            withAccount { account ->
                launchCatching(onStorageError = ::sendStorageFailed) {
                    when (val outcome = reconcile(account.id, actual)) {
                        is Outcome.Ok -> setState { copy(dialog = null) }
                        is Outcome.Err -> sendEffect(AccountEditEffect.ShowReconcileFailed(outcome.error))
                    }
                }
            }

        private fun addMask(
            kind: IdentifierKind,
            mask: String,
        ) = withAccount { account ->
            launchCatching(onStorageError = ::sendStorageFailed) {
                when (val outcome = addIdentifier(account.id, kind, mask)) {
                    is Outcome.Ok -> setState { copy(dialog = null) }
                    is Outcome.Err -> onMaskRefused(outcome.error)
                }
            }
        }

        /** A key held by an account that may merge in offers that merge; every other refusal stays a message. */
        private suspend fun onMaskRefused(error: AccountError) {
            if (error is AccountError.IdentifierTaken && error.mergeRejection == null) {
                offerMerge(error.holder)
            } else {
                sendEffect(AccountEditEffect.ShowError(error))
            }
        }

        private suspend fun offerMerge(holder: Account) {
            val stored = state.value.stored ?: return
            val draft = state.value.draft ?: return
            // The merge removes this account, so a pending edit is saved first. A refused save stops here.
            if (!draft.isDirtyAgainst(stored) || saveEdit(stored, draft)) {
                openMerge(draft.toAccount(stored, stored.id), holder)
            }
        }

        private fun removeMask(identifier: AccountIdentifier) =
            withAccount { account ->
                if (isLastIdentifier(account, state.value.identifiers)) {
                    setState { copy(dialog = AccountEditDialog.RemoveLastIdentifier(identifier)) }
                } else {
                    act { removeIdentifier(identifier) }
                }
            }

        private fun confirmRemoveMask() {
            val dialog = state.value.dialog as? AccountEditDialog.RemoveLastIdentifier ?: return
            setState { copy(dialog = null) }
            act { removeIdentifier(dialog.identifier) }
        }

        private fun askIgnore() =
            withAccount { account ->
                launchCatching(onStorageError = ::sendStorageFailed) {
                    val count = countTrashable(account.id)
                    // The account may have gone while the count loaded.
                    if (state.value.stored?.id == account.id) {
                        setState { copy(dialog = AccountEditDialog.Ignore(trashableCount = count)) }
                    }
                }
            }

        private fun toggleTrash(isChecked: Boolean) =
            setState {
                val ignore = dialog as? AccountEditDialog.Ignore
                copy(dialog = ignore?.copy(shouldTrash = isChecked) ?: dialog)
            }

        private fun confirmIgnore() =
            withAccount { account ->
                val dialog = state.value.dialog as? AccountEditDialog.Ignore ?: return@withAccount
                // Ticked with nothing to trash must not trash: this is the only guard.
                val shouldTrash = dialog.shouldTrash && dialog.trashableCount > 0
                setState { copy(dialog = null) }
                act { setIgnored(account.id, ignored = true, shouldTrashOldTransactions = shouldTrash) }
            }

        private fun confirmDelete() =
            withAccount { account ->
                if (state.value.dialog == AccountEditDialog.Delete) {
                    setState { copy(dialog = null) }
                    finish(CloseReason.DELETED) { deleteAccount(account.id) }
                }
            }

        private fun askMergeTarget() =
            withAccount { account ->
                val targets = mergeCandidates(allAccounts, account)
                if (targets.isEmpty()) {
                    sendEffect(AccountEditEffect.ShowNoMergeTarget)
                } else {
                    setState { copy(dialog = AccountEditDialog.MergeTargetPicker(targets)) }
                }
            }

        private fun askMerge(targetId: String) =
            withAccount { account ->
                val picker = state.value.dialog as? AccountEditDialog.MergeTargetPicker
                picker?.targets?.firstOrNull { it.id == targetId }?.let { target ->
                    launchCatching(onStorageError = ::sendStorageFailed) { openMerge(account, target) }
                }
            }

        private fun confirmMerge() {
            val dialog = state.value.dialog as? AccountEditDialog.Merge ?: return
            setState { copy(dialog = null) }
            finish(CloseReason.MERGED) { mergeAccounts(dialog.source.id, dialog.target.id) }
        }

        private suspend fun openMerge(
            source: Account,
            target: Account,
        ) {
            val count = countMergeMoves(source.id)
            val droppedStart = getDroppedStart(source.id, target.id)
            // The account may have gone while the numbers loaded.
            if (state.value.stored?.id != source.id) return
            setState { copy(dialog = AccountEditDialog.Merge(source, target, count, droppedStart)) }
        }

        /** Runs a write; an `Err` becomes an [AccountEditEffect.ShowError]. True when it succeeded. */
        private suspend fun attempt(block: suspend () -> Outcome<*, AccountError>): Boolean =
            when (val outcome = block()) {
                is Outcome.Ok -> {
                    true
                }

                is Outcome.Err -> {
                    sendEffect(AccountEditEffect.ShowError(outcome.error))
                    false
                }
            }

        private suspend fun saving(block: suspend () -> Outcome<*, AccountError>): Boolean {
            setState { copy(isSaving = true) }
            try {
                return attempt(block)
            } finally {
                setState { copy(isSaving = false) }
            }
        }

        private fun act(block: suspend () -> Outcome<*, AccountError>) {
            launchCatching(onStorageError = ::sendStorageFailed) { attempt(block) }
        }

        /** An own delete or merge: the screen closes with [reason] once it worked. */
        private fun finish(
            reason: CloseReason,
            block: suspend () -> Outcome<*, AccountError>,
        ) {
            pendingClose = reason
            launchCatching(
                onStorageError = {
                    pendingClose = null
                    sendStorageFailed()
                },
            ) {
                if (attempt(block)) close(reason) else pendingClose = null
            }
        }

        private fun close(reason: CloseReason) {
            if (isClosed) return
            isClosed = true
            sendEffect(AccountEditEffect.Close(reason))
        }

        private fun sendStorageFailed() = sendEffect(AccountEditEffect.ShowStorageFailed)
    }
