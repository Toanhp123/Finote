package io.github.toanhp123.finote.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.transaction.TransactionDay
import io.github.toanhp123.finote.domain.transaction.usecase.ObserveTransactionListUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

private const val SELECTED_ACCOUNT_KEY = "selected_account_id"

@HiltViewModel
class TransactionListViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeList: ObserveTransactionListUseCase,
        accountRepository: AccountRepository,
        categoryRepository: CategoryRepository,
        private val clock: Clock,
    ) : BaseViewModel<TransactionListState, TransactionListIntent, TransactionListEffect>(
            TransactionListState(today = LocalDate.now(clock)),
        ) {
        init {
            // The choice lives in the handle only, so it survives process death.
            val selected = savedStateHandle.getStateFlow<String?>(SELECTED_ACCOUNT_KEY, null)

            // A failed list becomes a null day list for that choice, so picking another chip retries.
            @OptIn(ExperimentalCoroutinesApi::class)
            val days =
                selected.flatMapLatest { id ->
                    observeList(id)
                        .map<List<TransactionDay>, Pair<String?, List<TransactionDay>?>> { id to it }
                        .catch { emit(id to null) }
                }
            viewModelScope.launch {
                combine(days, accountRepository.observeAccounts(), categoryRepository.observeCategories()) { d, a, c ->
                    Triple(d, a, c)
                }.catch {
                    // Not retried: a DB-level failure, and the list flow shares the same DB.
                    // No message or cause is logged: the content is financial data.
                    setState { copy(isLoading = false, hasError = true) }
                }.collect { (selectedDays, accountList, categoryList) ->
                    val (id, dayList) = selectedDays
                    val chips = accountList.filter { !it.isArchived }.sortedBy { it.sortOrder }
                    if (id != null && chips.none { it.id == id }) {
                        // The chosen account is gone; days of the stale choice are dropped, `days` re-runs for all.
                        savedStateHandle[SELECTED_ACCOUNT_KEY] = null
                    } else {
                        setState {
                            copy(
                                isLoading = false,
                                today = LocalDate.now(clock),
                                hasError = dayList == null,
                                accounts = chips,
                                selectedAccountId = id,
                                days = dayList.orEmpty(),
                                accountsById = accountList.associateBy { it.id },
                                categoriesById = categoryList.associateBy { it.id },
                            )
                        }
                    }
                }
            }
        }

        override fun onIntent(intent: TransactionListIntent) {
            when (intent) {
                is TransactionListIntent.SelectAccount -> savedStateHandle[SELECTED_ACCOUNT_KEY] = intent.accountId
            }
        }
    }
