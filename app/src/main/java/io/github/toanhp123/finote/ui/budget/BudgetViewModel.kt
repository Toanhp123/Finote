package io.github.toanhp123.finote.ui.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.budget.model.BudgetMonth
import io.github.toanhp123.finote.domain.budget.usecase.ObserveBudgetMonthUseCase
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.ui.common.BaseViewModel
import io.github.toanhp123.finote.ui.navigation.EditBudget
import io.github.toanhp123.finote.ui.navigation.ReportList
import io.github.toanhp123.finote.ui.navigation.ReportListKind
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
class BudgetViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeBudgetMonth: ObserveBudgetMonthUseCase,
        observeCategories: ObserveCategoriesUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<BudgetState, BudgetIntent, BudgetEffect>(
            BudgetState(
                month = savedMonth(savedStateHandle, clock),
                currentMonth = YearMonth.now(clock),
                today = LocalDate.now(clock),
            ),
            isDebuggable,
        ) {
        // The use case copies the current month forward on subscribe, so a new day resubscribes it.
        private val today = MutableStateFlow(LocalDate.now(clock))

        init {
            // The month lives in the handle only, so it survives process death.
            val months = savedStateHandle.getStateFlow(MONTH_KEY, YearMonth.now(clock).toString()).map(YearMonth::parse)

            // A failed month becomes a null result for that month, so moving to another month retries.
            @OptIn(ExperimentalCoroutinesApi::class)
            val budgets =
                combine(months.distinctUntilChanged(), today) { month, _ -> month }.flatMapLatest { month ->
                    observeBudgetMonth(month)
                        .map<BudgetMonth, Shown> { Shown(month, it) }
                        .catchStorage { emit(Shown(month, null)) }
                }
            viewModelScope.launch {
                combine(budgets, observeCategories()) { shown, categoryList -> shown to categoryList }
                    .catchStorage {
                        // Not retried: a DB-level failure, and the budget flow shares the same DB.
                        setState { copy(isLoading = false, hasError = true, total = null, rows = emptyList()) }
                    }.collect { (shown, categoryList) ->
                        // A result of the month the user just left can still arrive once; the new one follows.
                        if (shown.month != savedMonth(savedStateHandle, clock)) return@collect
                        setState { shownState(shown, categoryList) }
                    }
            }
        }

        private fun BudgetState.shownState(
            shown: Shown,
            categoryList: List<Category>,
        ): BudgetState {
            val result = shown.result
            val byId = categoryList.associateBy { it.id }
            // A budget whose category is gone has nothing to name it, so it is left out.
            val rows =
                result?.categories.orEmpty().mapNotNull { status ->
                    byId[status.budget.categoryId]?.let { BudgetRowUi(status, it) }
                }
            val current = YearMonth.now(clock)
            return copy(
                isLoading = false,
                hasError = result == null,
                month = shown.month,
                currentMonth = current,
                today = LocalDate.now(clock),
                total = result?.total,
                rows = rows,
                isAddEnabled = shown.month == current && result != null && hasRoomToAdd(result, categoryList),
            )
        }

        override fun onIntent(intent: BudgetIntent) {
            when (intent) {
                BudgetIntent.Resumed -> {
                    today.value = LocalDate.now(clock)
                    setState { copy(currentMonth = YearMonth.now(clock), today = LocalDate.now(clock)) }
                }

                BudgetIntent.PreviousMonth -> {
                    moveTo(savedMonth(savedStateHandle, clock).minusMonths(1))
                }

                BudgetIntent.NextMonth -> {
                    // The handle, not the state, is the truth: a quick second tap must not repeat the first move.
                    val saved = savedMonth(savedStateHandle, clock)
                    if (saved < state.value.currentMonth) moveTo(saved.plusMonths(1))
                }

                BudgetIntent.TotalClicked -> {
                    openReport(ReportList(state.value.month.toString(), ReportListKind.ALL_SPENDING))
                }

                is BudgetIntent.RowClicked -> {
                    openReport(ReportList(state.value.month.toString(), ReportListKind.CATEGORY, intent.categoryId))
                }

                is BudgetIntent.EditClicked -> {
                    sendEffect(BudgetEffect.OpenEditor(EditBudget(intent.budgetId)))
                }

                BudgetIntent.AddClicked -> {
                    sendEffect(BudgetEffect.OpenEditor(EditBudget()))
                }
            }
        }

        private fun openReport(key: ReportList) = sendEffect(BudgetEffect.OpenReport(key))

        // The header moves at once and the body loads, so it never names the old month over the new one's wait.
        private fun moveTo(month: YearMonth) {
            // Loading first: a result of the new month cannot slip in between and then be overwritten by loading.
            setState { copy(month = month, isLoading = true, isAddEnabled = false) }
            savedStateHandle[MONTH_KEY] = month.toString()
        }

        /** The budgets of [month]; a null [result] is a failed month. */
        private data class Shown(
            val month: YearMonth,
            val result: BudgetMonth?,
        )
    }

private const val MONTH_KEY = "month"

// Written as "yyyy-MM" by moveTo; a missing key means the first start.
private fun savedMonth(
    handle: SavedStateHandle,
    clock: Clock,
): YearMonth = handle.get<String>(MONTH_KEY)?.let(YearMonth::parse) ?: YearMonth.now(clock)

// The total, or a shown top-level expense category that has no budget yet.
private fun hasRoomToAdd(
    month: BudgetMonth,
    categories: List<Category>,
): Boolean {
    val budgeted = month.categories.mapNotNull { it.budget.categoryId }.toSet()
    return month.total == null ||
        categories.any {
            it.parentId == null && it.type == CategoryType.EXPENSE && !it.isHidden && it.id !in budgeted
        }
}
