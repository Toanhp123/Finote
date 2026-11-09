package io.github.toanhp123.finote.ui.transactions

import android.database.SQLException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.account.usecase.ObserveBalanceDriftUseCase
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionDay
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.ReconcileBalanceUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.transactions.component.ReimbursementChoice
import io.github.toanhp123.finote.ui.transactions.component.applyTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
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
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        private val updateTransaction: UpdateTransactionUseCase,
        observeDrifts: ObserveBalanceDriftUseCase,
        private val reconcileBalance: ReconcileBalanceUseCase,
        private val clock: Clock,
    ) : BaseViewModel<TransactionListState, TransactionListIntent, TransactionListEffect>(
            TransactionListState(today = LocalDate.now(clock)),
        ) {
        // The write in flight; another pick is ignored until it ends.
        private var pickJob: Job? = null

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
                            .catch { emit(Listing(id, isPending, null)) }
                    }
            viewModelScope.launch {
                combine(
                    listing,
                    accountRepository.observeAccounts(),
                    categoryRepository.observeCategories(),
                ) { l, a, c ->
                    Triple(l, a, c)
                }.catch {
                    // Not retried: a DB-level failure, and the list flow shares the same DB.
                    // No message or cause is logged: the content is financial data.
                    setState { copy(isLoading = false, hasError = true, editingId = null, hasSaveError = false) }
                }.collect { (list, accountList, categoryList) ->
                    val id = list.accountId
                    val chips = accountList.filter { !it.isArchived }.sortedBy { it.sortOrder }
                    if (id != null && chips.none { it.id == id }) {
                        // The chosen account is gone; days of the stale choice are dropped, `days` re-runs for all.
                        savedStateHandle[SELECTED_ACCOUNT_KEY] = null
                    } else {
                        val dayList = list.days.orEmpty()
                        setState {
                            // The open row is gone from the new list (deleted, changed, list failed): close its sheet.
                            val isEditingKept = dayList.containsTransaction(editingId)
                            copy(
                                isLoading = false,
                                today = LocalDate.now(clock),
                                hasError = list.days == null,
                                accounts = chips,
                                selectedAccountId = id,
                                isPendingOnly = list.isPendingOnly,
                                days = dayList,
                                editingId = editingId.takeIf { isEditingKept },
                                hasSaveError = hasSaveError && isEditingKept,
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
                    .catch { emit(emptyList()) }
                    .collect { setState { copy(drifts = it) } }
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
                    openSheet(intent.id)
                }

                is TransactionListIntent.ReimbursementPicked -> {
                    pick(intent.choice)
                }

                TransactionListIntent.SheetDismissed -> {
                    setState { copy(editingId = null, hasSaveError = false) }
                }

                is TransactionListIntent.AdjustDrift -> {
                    adjust(intent.accountId)
                }
            }
        }

        private fun findTransaction(id: String) =
            state.value.days
                .asSequence()
                .flatMap { it.transactions }
                .firstOrNull { it.id == id }

        // Only an expense on a live account can be reimbursable (the update use case rejects an archived account);
        // the screen gives other rows no click, this guards the intent.
        private fun openSheet(id: String) {
            val transaction = findTransaction(id) ?: return
            val isOnLiveAccount = state.value.accounts.any { it.id == transaction.accountId }
            if (transaction.type == TransactionType.EXPENSE && isOnLiveAccount) {
                setState { copy(editingId = id, hasSaveError = false) }
            }
        }

        private fun pick(choice: ReimbursementChoice) {
            val transaction = state.value.editingId?.let(::findTransaction)
            if (pickJob?.isActive == true || transaction == null) return
            val updated = choice.applyTo(transaction)
            if (updated == transaction) {
                setState { copy(editingId = null, hasSaveError = false) }
            } else {
                write(updated)
            }
        }

        // The drift flow re-emits once the adjustment lands, which removes the banner.
        private fun adjust(accountId: String) {
            val drift = state.value.drifts.firstOrNull { it.accountId == accountId } ?: return
            val account = state.value.accountsById[accountId] ?: return
            viewModelScope.launch {
                try {
                    reconcileBalance(accountId, Math.addExact(account.currentBalance, drift.difference))
                } catch (_: IllegalArgumentException) {
                    // The banner stays, so the user can tap again.
                } catch (_: SQLException) {
                    // Same: no message is logged because the content is financial data.
                }
            }
        }

        private fun write(updated: Transaction) {
            // Cleared first, so a repeated failure changes the flag again and is announced again.
            setState { copy(hasSaveError = false) }
            pickJob =
                viewModelScope.launch {
                    try {
                        updateTransaction(updated)
                        setState { copy(editingId = null, hasSaveError = false) }
                    } catch (_: IllegalArgumentException) {
                        setState { copy(hasSaveError = true) }
                    } catch (_: SQLException) {
                        setState { copy(hasSaveError = true) }
                    }
                }
        }

        private fun List<TransactionDay>.containsTransaction(id: String?): Boolean =
            id != null && any { day -> day.transactions.any { it.id == id } }

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
