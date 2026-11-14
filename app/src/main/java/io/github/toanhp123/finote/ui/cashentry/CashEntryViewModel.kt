package io.github.toanhp123.finote.ui.cashentry

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class CashEntryViewModel
    @Inject
    constructor(
        private val observeUsableAccounts: ObserveUsableAccountsUseCase,
        private val observeCategories: ObserveCategoriesUseCase,
        private val getLastManualChoices: GetLastManualChoicesUseCase,
        private val addManualTransaction: AddManualTransactionUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<CashEntryState, CashEntryIntent, CashEntryEffect>(
            CashEntryState(date = LocalDate.now(clock)),
            isDebuggable,
        ) {
        private var lastAccountId: String? = null
        private var isSeeded = false
        private var allCategories: List<Category> = emptyList()

        init {
            viewModelScope.launch {
                // The last-choice read is inside the flow, so a failure reaches catch; the lists then stay empty.
                combine(
                    flow { emit(getLastManualChoices()) },
                    observeUsableAccounts(),
                    observeCategories(),
                ) { choices, a, c -> Triple(choices, a, c) }
                    .catchStorage {
                        // Save stays disabled with no accounts. Nothing is logged: accounts and notes are private.
                    }.collect { (choices, a, c) -> onLists(choices, a, c) }
            }
        }

        override fun onIntent(intent: CashEntryIntent) {
            when (intent) {
                is CashEntryIntent.KeyPressed -> {
                    onKey(intent)
                }

                is CashEntryIntent.TypeChanged -> {
                    setState { copy(type = intent.type, categories = visible(intent.type)) }
                }

                is CashEntryIntent.AccountPicked -> {
                    onAccount(intent.id)
                }

                is CashEntryIntent.CategoryPicked -> {
                    setState { pickCategory(intent.id).backToKeypad() }
                }

                is CashEntryIntent.DatePicked -> {
                    setState {
                        copy(date = intent.date, isToday = intent.date == LocalDate.now(clock)).backToKeypad()
                    }
                }

                is CashEntryIntent.NoteChanged -> {
                    setState { copy(note = intent.note.take(MAX_NOTE_LENGTH)) }
                }

                is CashEntryIntent.PageOpened, CashEntryIntent.PageClosed -> {
                    onPage(intent)
                }

                CashEntryIntent.SaveClicked -> {
                    save()
                }

                CashEntryIntent.Dismissed -> {
                    sendEffect(CashEntryEffect.Close)
                }
            }
        }

        // Only PageOpened and PageClosed reach here: any other intent counts as a close.
        private fun onPage(intent: CashEntryIntent) =
            setState { if (intent is CashEntryIntent.PageOpened) copy(page = intent.page) else backToKeypad() }

        // An id that is not in the live list (for example archived while the page was open) changes nothing.
        private fun onAccount(id: String) =
            setState { if (accounts.any { it.id == id }) copy(selectedAccountId = id).backToKeypad() else this }

        private fun CashEntryState.backToKeypad() = copy(page = CashEntryPage.KEYPAD)

        private fun CashEntryState.pickCategory(id: String?) =
            copy(selectedCategoryIds = selectedCategoryIds + (type to id))

        private fun onKey(intent: CashEntryIntent.KeyPressed) =
            setState {
                val next = AmountExpression.press(expression, intent.key)
                copy(expression = next, result = AmountExpression.evaluate(next), hasSaveError = false)
            }

        private fun onLists(
            choices: ManualChoices,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            allCategories = categoryList
            lastAccountId = choices.accountId
            // Seed with the last used ids on the first value only; every value validates them.
            val seed = !isSeeded
            isSeeded = true
            setState {
                val chosen = if (seed) choices.categoryIds else selectedCategoryIds
                val account =
                    selectedAccountId?.takeIf { id -> accountList.any { it.id == id } }
                        ?: CashEntryDefaults.pickAccount(accountList, lastAccountId)
                val kept =
                    CATEGORY_TYPES.associateWith {
                        CashEntryDefaults.pickCategory(categoryList, chosen[it], it.toCategoryType())
                    }
                copy(
                    accounts = accountList,
                    selectedAccountId = account,
                    selectedCategoryIds = kept,
                    categories = visible(type),
                )
            }
        }

        private fun visible(type: TransactionType): List<Category> =
            allCategories
                .filter { it.type == type.toCategoryType() && it.deletedAt == null && !it.isHidden }
                .sortedBy { it.sortOrder }

        private fun save() {
            val s = state.value
            val amount = (s.result as? AmountResult.Ok)?.amount
            val accountId = s.selectedAccountId
            if (!s.isSaveEnabled || amount == null || accountId == null) return
            setState { copy(isSaving = true, hasSaveError = false) }
            launchCatching(onStorageError = ::showSaveError) {
                val result =
                    addManualTransaction(
                        amount = amount,
                        type = s.type,
                        accountId = accountId,
                        categoryId = s.selectedCategoryIds[s.type],
                        date = s.date,
                        note = s.note,
                    )
                when (result) {
                    is Outcome.Ok -> sendEffect(CashEntryEffect.Close)
                    is Outcome.Err -> showSaveError()
                }
            }
        }

        private fun showSaveError() = setState { copy(isSaving = false, hasSaveError = true) }

        private fun TransactionType.toCategoryType() =
            if (this == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

        companion object {
            const val MAX_NOTE_LENGTH = 200
            private val CATEGORY_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
        }
    }
