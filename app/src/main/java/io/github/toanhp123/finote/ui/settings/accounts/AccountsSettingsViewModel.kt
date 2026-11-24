package io.github.toanhp123.finote.ui.settings.accounts

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.usecase.DismissMergeSuggestionUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBankAppsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveMergeSuggestionsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ReorderAccountsUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.settings.accounts.logic.AccountListItem
import io.github.toanhp123.finote.ui.settings.accounts.logic.groupAccounts
import io.github.toanhp123.finote.ui.settings.accounts.logic.reorderedIds
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountsSettingsViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @Inject
    constructor(
        observeAccounts: ObserveAccountsUseCase,
        observeBankApps: ObserveBankAppsUseCase,
        observeSuggestions: ObserveMergeSuggestionsUseCase,
        private val dismissSuggestion: DismissMergeSuggestionUseCase,
        private val reorderAccounts: ReorderAccountsUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<AccountsSettingsState, AccountsSettingsIntent, AccountsSettingsEffect>(
            AccountsSettingsState(),
            isDebuggable,
        ) {
        init {
            viewModelScope.launch {
                combine(observeAccounts(), observeBankApps(), observeSuggestions()) { accounts, apps, suggestions ->
                    val names = apps.associate { it.packageName to it.bankName }
                    val archived = accounts.filter { it.isArchived && it.deletedAt == null }
                    AccountsSettingsState(
                        items = groupAccounts(accounts, names),
                        archived = archived.sortedBy { it.sortOrder },
                        suggestions = suggestions,
                    )
                }.catchStorage {
                    // The list keeps its last state. Nothing is logged: accounts and balances are private.
                }.collect { loaded ->
                    setState {
                        copy(items = loaded.items, archived = loaded.archived, suggestions = loaded.suggestions)
                    }
                }
            }
        }

        override fun onIntent(intent: AccountsSettingsIntent) {
            when (intent) {
                is AccountsSettingsIntent.AccountClicked -> {
                    sendEffect(AccountsSettingsEffect.OpenAccount(intent.id))
                }

                AccountsSettingsIntent.AddClicked -> {
                    sendEffect(AccountsSettingsEffect.OpenAccount(id = null))
                }

                is AccountsSettingsIntent.GroupToggled -> {
                    setState { copy(expanded = expanded.toggled(intent.packageName)) }
                }

                AccountsSettingsIntent.ArchivedToggled -> {
                    setState { copy(isArchivedExpanded = !isArchivedExpanded) }
                }

                is AccountsSettingsIntent.SuggestionClicked -> {
                    sendEffect(AccountsSettingsEffect.OpenAccount(intent.id, state.value.suggestions[intent.id]))
                }

                is AccountsSettingsIntent.SuggestionDismissed -> {
                    dismiss(intent.id)
                }

                AccountsSettingsIntent.ReorderClicked -> {
                    setState { copy(reorder = items) }
                }

                is AccountsSettingsIntent.ReorderMoved -> {
                    setState { copy(reorder = reorder?.moved(intent.from, intent.to)) }
                }

                AccountsSettingsIntent.ReorderDone -> {
                    finishReorder()
                }

                AccountsSettingsIntent.ReorderCancelled -> {
                    setState { copy(reorder = null) }
                }
            }
        }

        private fun dismiss(id: String) {
            val partner = state.value.suggestions[id] ?: return
            launchCatching(onStorageError = ::sendActionFailed) { dismissSuggestion(id, partner) }
        }

        private fun finishReorder() {
            val draft = state.value.reorder ?: return
            // Leaves the mode first, so a second tap on Done finds nothing to write.
            setState { copy(reorder = null) }
            // Archived accounts follow the live ones, so every account ends with its own sort order.
            val ids = reorderedIds(draft) + state.value.archived.map { it.id }
            launchCatching(onStorageError = ::sendActionFailed) {
                if (reorderAccounts(ids) is Outcome.Err) sendActionFailed()
            }
        }

        private fun sendActionFailed() = sendEffect(AccountsSettingsEffect.ActionFailed)
    }

private fun Set<String>.toggled(value: String) = if (value in this) this - value else this + value

private fun List<AccountListItem>.moved(
    from: Int,
    to: Int,
): List<AccountListItem> =
    if (from !in indices || to !in indices) this else toMutableList().apply { add(to, removeAt(from)) }
