package io.github.toanhp123.finote.ui.report

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.ObserveAccountsUseCase
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.report.usecase.ObserveReportRowsUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.navigation.ReportList
import io.github.toanhp123.finote.ui.report.logic.toFilter
import io.github.toanhp123.finote.ui.report.logic.yearMonth
import io.github.toanhp123.finote.ui.transactions.logic.isOpenable
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The live rows of one report slice. The list follows the database, so a row moved away leaves it at once. */
@HiltViewModel(assistedFactory = ReportListViewModel.Factory::class)
class ReportListViewModel
    @AssistedInject
    constructor(
        @Assisted key: ReportList,
        observeRows: ObserveReportRowsUseCase,
        observeAccounts: ObserveAccountsUseCase,
        observeCategories: ObserveCategoriesUseCase,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<ReportListState, ReportListIntent, ReportListEffect>(
            ReportListState(month = key.yearMonth(), filter = key.toFilter()),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(key: ReportList): ReportListViewModel
        }

        init {
            viewModelScope.launch {
                combine(
                    observeRows(state.value.month, state.value.filter),
                    observeAccounts(),
                    observeCategories(),
                ) { rows, accountList, categoryList ->
                    Triple(rows, accountList, categoryList)
                }.catchStorage {
                    // No message or cause is logged: the content is financial data.
                    setState { copy(isLoading = false, hasError = true) }
                }.collect { (rows, accountList, categoryList) ->
                    setState {
                        copy(
                            isLoading = false,
                            hasError = false,
                            rows = rows.rows,
                            total = rows.total,
                            accountsById = accountList.associateBy(Account::id),
                            categoriesById = categoryList.associateBy { it.id },
                            // Same rule as the transaction list: an archived or ignored account is read-only.
                            openableAccountIds = accountList.filter(Account::isUsable).mapTo(mutableSetOf()) { it.id },
                        )
                    }
                }
            }
        }

        override fun onIntent(intent: ReportListIntent) {
            when (intent) {
                is ReportListIntent.RowClicked -> {
                    val current = state.value
                    val row = current.rows.firstOrNull { it.transaction.id == intent.id }
                    if (row != null && row.transaction.isOpenable(current.openableAccountIds)) {
                        sendEffect(ReportListEffect.OpenEditor(intent.id))
                    }
                }
            }
        }
    }
