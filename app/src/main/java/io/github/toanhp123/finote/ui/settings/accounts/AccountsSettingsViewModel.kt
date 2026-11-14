package io.github.toanhp123.finote.ui.settings.accounts

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.CountMergeMovesUseCase
import io.github.toanhp123.finote.domain.account.usecase.CountTrashableTransactionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.MergeAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetAccountIgnoredUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsSettingsViewModel
    @Inject
    constructor(
        observeAccounts: ObserveAccountsUseCase,
        private val countMergeMoves: CountMergeMovesUseCase,
        private val countTrashable: CountTrashableTransactionsUseCase,
        private val mergeAccounts: MergeAccountsUseCase,
        private val setIgnored: SetAccountIgnoredUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<AccountsSettingsState, AccountsSettingsIntent, AccountsSettingsEffect>(
            AccountsSettingsState(),
            isDebuggable,
        ) {
        init {
            viewModelScope.launch {
                observeAccounts()
                    .catchStorage {
                        // The list keeps its last state. Nothing is logged: accounts and balances are private.
                    }.collect { list ->
                        setState { copy(accounts = list.sortedWith(compareBy({ it.isIgnored }, { it.sortOrder }))) }
                    }
            }
        }

        override fun onIntent(intent: AccountsSettingsIntent) {
            when (intent) {
                is AccountsSettingsIntent.AccountClicked -> {
                    openSheet(intent.id)
                }

                AccountsSettingsIntent.SheetDismissed -> {
                    setState {
                        copy(
                            selected = null,
                            mergeTargets = emptyList(),
                        ).withoutDialogs()
                    }
                }

                AccountsSettingsIntent.DialogDismissed -> {
                    setState { withoutDialogs() }
                }

                is AccountsSettingsIntent.MergeTargetPicked -> {
                    askMerge(intent.targetId)
                }

                AccountsSettingsIntent.MergeConfirmed -> {
                    confirmMerge()
                }

                AccountsSettingsIntent.IgnoreClicked -> {
                    askIgnore()
                }

                is AccountsSettingsIntent.IgnoreConfirmed -> {
                    confirmIgnore(intent.shouldTrash)
                }

                AccountsSettingsIntent.FollowAgainClicked -> {
                    followAgain()
                }
            }
        }

        private fun openSheet(id: String) {
            val account = state.value.accounts.firstOrNull { it.id == id }
            // An account without a bank key (cash) has no action to offer.
            val bank = account?.bankPackageName ?: return
            val targets = state.value.accounts.filter { it.id != id && it.bankPackageName == bank }
            setState { copy(selected = account, mergeTargets = targets) }
        }

        private fun askMerge(targetId: String) {
            val source = state.value.selected ?: return
            val target = state.value.mergeTargets.firstOrNull { it.id == targetId } ?: return
            launchCatching(onStorageError = ::sendActionFailed) {
                val count = countMergeMoves(source.id)
                // The sheet may have closed while the count loaded.
                if (state.value.selected?.id != source.id) return@launchCatching
                setState { copy(pendingMerge = source to target, mergeCount = count) }
            }
        }

        private fun confirmMerge() {
            val (source, target) = state.value.pendingMerge ?: return
            finishAction(AccountsSettingsEffect.Merged) { mergeAccounts(source.id, target.id) }
        }

        private fun askIgnore() {
            val account = state.value.selected ?: return
            launchCatching(onStorageError = ::sendActionFailed) {
                val count = countTrashable(account.id)
                if (state.value.selected?.id != account.id) return@launchCatching
                setState { copy(isIgnoreDialogOpen = true, trashableCount = count) }
            }
        }

        private fun confirmIgnore(shouldTrash: Boolean) {
            val account = state.value.selected ?: return
            finishAction(AccountsSettingsEffect.Ignored) { setIgnored(account.id, ignored = true, shouldTrash) }
        }

        private fun followAgain() {
            val account = state.value.selected ?: return
            finishAction(AccountsSettingsEffect.FollowingAgain) {
                setIgnored(account.id, ignored = false, shouldTrashOldTransactions = false)
            }
        }

        /**
         * Closes the sheet and dialogs first, so a second tap finds nothing to confirm. Then runs a write and reports
         * the outcome as one effect.
         */
        private fun finishAction(
            success: AccountsSettingsEffect,
            block: suspend () -> Outcome<Unit, AccountError>,
        ) {
            setState { copy(selected = null, mergeTargets = emptyList()).withoutDialogs() }
            launchCatching(onStorageError = ::sendActionFailed) { sendEffect(effectOf(block(), success)) }
        }

        private fun sendActionFailed() = sendEffect(AccountsSettingsEffect.ActionFailed)

        private fun effectOf(
            outcome: Outcome<Unit, AccountError>,
            success: AccountsSettingsEffect,
        ): AccountsSettingsEffect =
            when (outcome) {
                is Outcome.Ok -> success
                is Outcome.Err -> blockOrFail(outcome.error)
            }

        private fun blockOrFail(error: AccountError): AccountsSettingsEffect =
            if (error == AccountError.MergeRejected(MergeRejection.HAS_TRANSFER)) {
                AccountsSettingsEffect.MergeBlockedByTransfer
            } else {
                AccountsSettingsEffect.ActionFailed
            }

        private fun AccountsSettingsState.withoutDialogs() =
            copy(pendingMerge = null, mergeCount = 0, isIgnoreDialogOpen = false, trashableCount = 0)
    }
