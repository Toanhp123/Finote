package io.github.toanhp123.finote.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.account.usecase.ObserveBalanceDriftUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveAutoPairedIdsUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.ReconcileBalanceUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.transactions.logic.isOpenable
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeList: ObserveTransactionListUseCase,
        observeAccounts: ObserveAccountsUseCase,
        observeCategories: ObserveCategoriesUseCase,
        observeDrifts: ObserveBalanceDriftUseCase,
        private val reconcileBalance: ReconcileBalanceUseCase,
        private val deleteTransaction: DeleteTransactionUseCase,
        observeAutoPairedIds: ObserveAutoPairedIdsUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<TransactionListState, TransactionListIntent, TransactionListEffect>(
            TransactionListState(today = LocalDate.now(clock)),
            isDebuggable,
        ) {
        init {
            // The choices live in the handle only, so they survive process death.
            val selected = savedStateHandle.getStateFlow<String?>(SELECTED_ACCOUNT_KEY, null)
            val pending = savedStateHandle.getStateFlow(PENDING_ONLY_KEY, false)

            // A failed list becomes a null day list for that choice, so picking another chip retries.
            @OptIn(ExperimentalCoroutinesApi::class)
            val listing =
                combine(selected, pending) { id, isPending -> if (isPending) null to true else id to false }
                    .flatMapLatest { (id, isPending) ->
                        observeList(id, isPending)
                            .map<List<TransactionDay>, Listing> { Listing(id, isPending, it) }
                            .catchStorage { emit(Listing(id, isPending, null)) }
                    }
            viewModelScope.launch {
                combine(
                    listing,
                    observeAccounts(),
                    observeCategories(),
                ) { l, a, c ->
                    Triple(l, a, c)
                }.catchStorage {
                    // Not retried: a DB-level failure, and the list flow shares the same DB.
                    // No message or cause is logged: the content is financial data.
                    setState { copy(isLoading = false, hasError = true) }
                }.collect { (list, accountList, categoryList) ->
                    val id = list.accountId
                    val chips = accountList.filter(Account::isUsable)
                    if (id != null && chips.none { it.id == id }) {
                        // The chosen account is gone; days of the stale choice are dropped, `days` re-runs for all.
                        savedStateHandle[SELECTED_ACCOUNT_KEY] = null
                    } else {
                        val dayList = list.days.orEmpty()
                        setState {
                            copy(
                                isLoading = false,
                                today = LocalDate.now(clock),
                                hasError = list.days == null,
                                accounts = chips,
                                selectedAccountId = id,
                                isPendingOnly = list.isPendingOnly,
                                days = dayList,
                                accountsById = accountList.associateBy { it.id },
                                categoriesById = categoryList.associateBy { it.id },
                            )
                        }
                    }
                }
            }
        }

        init {
            // A failed drift flow only hides the banner; the list has its own error state.
            viewModelScope.launch {
                observeDrifts()
                    .catchStorage { emit(emptyList()) }
                    .collect { setState { copy(drifts = it) } }
            }
            // Same for the label: a failed flow only drops it.
            viewModelScope.launch {
                observeAutoPairedIds()
                    .catchStorage { emit(emptySet()) }
                    .collect { setState { copy(autoPairedIds = it) } }
            }
        }

        override fun onIntent(intent: TransactionListIntent) {
            when (intent) {
                is TransactionListIntent.SelectAccount -> {
                    savedStateHandle[PENDING_ONLY_KEY] = false
                    savedStateHandle[SELECTED_ACCOUNT_KEY] = intent.accountId
                }

                TransactionListIntent.SelectPending -> {
                    savedStateHandle[SELECTED_ACCOUNT_KEY] = null
                    savedStateHandle[PENDING_ONLY_KEY] = true
                }

                is TransactionListIntent.RowClicked -> {
                    openRow(intent.id)
                }

                TransactionListIntent.DeleteConfirmed -> {
                    deleteAdjustment()
                }

                TransactionListIntent.DeleteDismissed -> {
                    setState { copy(deletingId = null) }
                }

                is TransactionListIntent.ReconcileClicked -> {
                    setState { copy(reconcilingAccountId = intent.accountId) }
                }

                is TransactionListIntent.ReconcileSubmitted -> {
                    reconcile(intent.actualBalance)
                }

                TransactionListIntent.ReconcileDismissed -> {
                    setState { copy(reconcilingAccountId = null) }
                }
            }
        }

        // A row on an archived or ignored account is not openable: the editor could not pick it as a source.
        private fun openRow(id: String) {
            val current = state.value
            val liveIds = current.accounts.mapTo(mutableSetOf()) { it.id }
            val row = current.days.firstNotNullOfOrNull { day -> day.transactions.firstOrNull { it.id == id } }
            when {
                row == null || !row.isOpenable(liveIds) -> {}

                row.type == TransactionType.ADJUSTMENT -> {
                    setState { copy(deletingId = id) }
                }

                else -> {
                    sendEffect(TransactionListEffect.OpenEditor(id))
                }
            }
        }

        // Clearing deletingId first makes a second confirm do nothing. Nothing is logged: financial data.
        private fun deleteAdjustment() {
            val id = state.value.deletingId ?: return
            setState { copy(deletingId = null) }
            launchCatching(onStorageError = ::sendDeleteFailed) {
                if (deleteTransaction(id) is Outcome.Err) sendDeleteFailed()
            }
        }

        private fun sendDeleteFailed() = sendEffect(TransactionListEffect.DeleteFailed)

        // The sheet closes only on success, so a failed save keeps what the user typed. The drift flow re-emits once
        // the adjustment or the acknowledgement lands, which removes the banner. Nothing is logged: financial data.
        private fun reconcile(actualBalance: Long) {
            val accountId = state.value.reconcilingAccountId ?: return
            launchCatching(onStorageError = ::sendReconcileFailed) {
                when (reconcileBalance(accountId, actualBalance)) {
                    is Outcome.Ok -> setState { copy(reconcilingAccountId = null) }
                    is Outcome.Err -> sendReconcileFailed()
                }
            }
        }

        private fun sendReconcileFailed() = sendEffect(TransactionListEffect.ReconcileFailed)

        /** The list for one filter choice; a null [days] is a failed list. */
        private data class Listing(
            val accountId: String?,
            val isPendingOnly: Boolean,
            val days: List<TransactionDay>?,
        )

        private companion object {
            const val SELECTED_ACCOUNT_KEY = "selected_account_id"
            const val PENDING_ONLY_KEY = "pending_only"
        }
    }
