package io.github.toanhp123.finote.ui.settings.accounts

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountMergeRejectedException
import io.github.toanhp123.finote.domain.account.model.MergeRejection
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.usecase.MergeAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.SetAccountIgnoredUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsSettingsViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
        private val mergeAccounts: MergeAccountsUseCase,
        private val setIgnored: SetAccountIgnoredUseCase,
    ) : BaseViewModel<AccountsSettingsState, AccountsSettingsIntent, AccountsSettingsEffect>(AccountsSettingsState()) {
        init {
            viewModelScope.launch {
                accounts
                    .observeAccounts()
                    .catch {
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
            viewModelScope.launch {
                var count = 0
                val failure = failureOf { count = accounts.getLiveTransactionCount(source.id) }
                if (failure != null) return@launch sendEffect(failure)
                // The sheet may have closed while the count loaded.
                if (state.value.selected?.id != source.id) return@launch
                setState { copy(pendingMerge = source to target, mergeCount = count) }
            }
        }

        private fun confirmMerge() {
            val (source, target) = state.value.pendingMerge ?: return
            finishAction(AccountsSettingsEffect.Merged) { mergeAccounts(source.id, target.id) }
        }

        private fun askIgnore() {
            val account = state.value.selected ?: return
            viewModelScope.launch {
                var count = 0
                val failure = failureOf { count = accounts.getTrashableTransactionCount(account.id) }
                if (failure != null) return@launch sendEffect(failure)
                if (state.value.selected?.id != account.id) return@launch
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
            block: suspend () -> Unit,
        ) {
            setState { copy(selected = null, mergeTargets = emptyList()).withoutDialogs() }
            viewModelScope.launch { sendEffect(failureOf(block) ?: success) }
        }

        /** Null when [block] works. Any failure but cancellation becomes an effect, so a bad write never crashes. */
        private suspend fun failureOf(block: suspend () -> Unit): AccountsSettingsEffect? =
            try {
                block()
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: AccountMergeRejectedException) {
                if (e.reason == MergeRejection.HAS_TRANSFER) {
                    AccountsSettingsEffect.MergeBlockedByTransfer
                } else {
                    AccountsSettingsEffect.ActionFailed
                }
            } catch (_: Exception) {
                AccountsSettingsEffect.ActionFailed
            }

        private fun AccountsSettingsState.withoutDialogs() =
            copy(pendingMerge = null, mergeCount = 0, isIgnoreDialogOpen = false, trashableCount = 0)
    }
