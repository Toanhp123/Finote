package io.github.toanhp123.finote.ui.overview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.report.model.MonthReport
import io.github.toanhp123.finote.domain.report.model.ReportFilter
import io.github.toanhp123.finote.domain.report.usecase.ObserveHasTransactionsUseCase
import io.github.toanhp123.finote.domain.report.usecase.ObserveMonthReportUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class OverviewViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeMonthReport: ObserveMonthReportUseCase,
        observeHasTransactions: ObserveHasTransactionsUseCase,
        observeCategories: ObserveCategoriesUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<OverviewState, OverviewIntent, OverviewEffect>(
            OverviewState(month = savedMonth(savedStateHandle, clock), currentMonth = YearMonth.now(clock)),
            isDebuggable,
        ) {
        // The report reads its own "today" on subscribe, so a new value here resubscribes and moves the pace.
        private val today = MutableStateFlow(LocalDate.now(clock))

        init {
            // The month lives in the handle only, so it survives process death.
            val months = savedStateHandle.getStateFlow(MONTH_KEY, YearMonth.now(clock).toString()).map(YearMonth::parse)

            // A failed month becomes a null report for that month, so moving to another month retries.
            @OptIn(ExperimentalCoroutinesApi::class)
            val reports =
                combine(months.distinctUntilChanged(), today) { month, _ -> month }.flatMapLatest { month ->
                    observeMonthReport(month)
                        .map<MonthReport, Shown> { Shown(month, it) }
                        .catchStorage { emit(Shown(month, null)) }
                }
            viewModelScope.launch {
                combine(reports, observeHasTransactions(), observeCategories()) { shown, hasRows, categoryList ->
                    Triple(shown, hasRows, categoryList)
                }.catchStorage {
                    // Not retried: a DB-level failure, and the report flow shares the same DB.
                    // No message or cause is logged: the content is financial data.
                    setState { copy(isLoading = false, hasError = true, report = null) }
                }.collect { (shown, hasRows, categoryList) ->
                    // A report of the month the user just left can still arrive once; the new one follows.
                    if (shown.month != savedMonth(savedStateHandle, clock)) return@collect
                    setState {
                        copy(
                            isLoading = false,
                            hasError = shown.report == null,
                            month = shown.month,
                            currentMonth = YearMonth.now(clock),
                            hasTransactions = hasRows,
                            report = shown.report,
                            categoriesById = categoryList.associateBy { it.id },
                        )
                    }
                }
            }
        }

        override fun onIntent(intent: OverviewIntent) {
            when (intent) {
                OverviewIntent.Resumed -> {
                    today.value = LocalDate.now(clock)
                    setState { copy(currentMonth = YearMonth.now(clock)) }
                }

                OverviewIntent.PreviousMonth -> {
                    moveTo(savedMonth(savedStateHandle, clock).minusMonths(1))
                }

                OverviewIntent.NextMonth -> {
                    // The handle, not the state, is the truth: a quick second tap must not repeat the first move.
                    val saved = savedMonth(savedStateHandle, clock)
                    if (saved < state.value.currentMonth) moveTo(saved.plusMonths(1))
                }

                OverviewIntent.ToggleBuckets -> {
                    setState { copy(isBucketListExpanded = !isBucketListExpanded) }
                }

                is OverviewIntent.BucketClicked -> {
                    sendEffect(OverviewEffect.OpenReport(state.value.month, intent.filter))
                }

                is OverviewIntent.MerchantClicked -> {
                    sendEffect(OverviewEffect.OpenReport(state.value.month, ReportFilter.ByMerchant(intent.name)))
                }
            }
        }

        // The header moves at once and the body loads, so it never names the old month over the new one's wait.
        private fun moveTo(month: YearMonth) {
            savedStateHandle[MONTH_KEY] = month.toString()
            setState { copy(month = month, isLoading = true) }
        }

        /** The report of [month]; a null [report] is a failed month. */
        private data class Shown(
            val month: YearMonth,
            val report: MonthReport?,
        )
    }

private const val MONTH_KEY = "month"

// Written as "yyyy-MM" by moveTo; a missing key means the first start.
private fun savedMonth(
    handle: SavedStateHandle,
    clock: Clock,
): YearMonth = handle.get<String>(MONTH_KEY)?.let(YearMonth::parse) ?: YearMonth.now(clock)
