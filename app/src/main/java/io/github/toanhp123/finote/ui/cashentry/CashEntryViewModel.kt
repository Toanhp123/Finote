package io.github.toanhp123.finote.ui.cashentry

import android.database.SQLException
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.repository.AccountRepository
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionSource
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import io.github.toanhp123.finote.domain.transaction.usecase.AddTransactionUseCase
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CashEntryViewModel
    @Inject
    constructor(
        private val accounts: AccountRepository,
        private val categories: CategoryRepository,
        private val transactions: TransactionRepository,
        private val addTransaction: AddTransactionUseCase,
        private val clock: Clock,
    ) : BaseViewModel<CashEntryState, CashEntryIntent, CashEntryEffect>(
            CashEntryState(date = LocalDate.now(clock)),
        ) {
        private var lastAccountId: String? = null
        private var allCategories: List<Category> = emptyList()

        init {
            viewModelScope.launch {
                lastAccountId = transactions.getLastManualAccountId()
                // Seed the choices with the last used ids; every emission below validates them.
                val lastCategoryIds = CATEGORY_TYPES.associateWith { transactions.getLastManualCategoryId(it) }
                setState { copy(selectedCategoryIds = lastCategoryIds) }
                combine(accounts.observeAccounts(), categories.observeCategories()) { a, c -> a to c }
                    .collect { (a, c) -> onLists(a, c) }
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
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            allCategories = categoryList
            val live = accountList.filter { it.deletedAt == null && !it.isArchived }.sortedBy { it.sortOrder }
            setState {
                val account =
                    selectedAccountId?.takeIf { id -> live.any { it.id == id } }
                        ?: CashEntryDefaults.pickAccount(accountList, lastAccountId)
                val kept =
                    CATEGORY_TYPES.associateWith {
                        CashEntryDefaults.pickCategory(categoryList, selectedCategoryIds[it], it.toCategoryType())
                    }
                copy(
                    accounts = live,
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
            val transaction =
                Transaction(
                    id = UUID.randomUUID().toString(),
                    amount = amount,
                    feeAmount = 0,
                    type = s.type,
                    adjustmentDirection = null,
                    accountId = accountId,
                    toAccountId = null,
                    timestamp = timestampOf(s.date),
                    deletedAt = null,
                    createdAt = 0,
                    updatedAt = 0,
                    categoryId = s.selectedCategoryIds[s.type],
                    note = s.note.trim(),
                    source = TransactionSource.MANUAL,
                )
            viewModelScope.launch {
                try {
                    addTransaction(transaction)
                    sendEffect(CashEntryEffect.Close)
                } catch (_: IllegalArgumentException) {
                    setState { copy(isSaving = false, hasSaveError = true) }
                } catch (_: SQLException) {
                    // androidx.sqlite.SQLiteException is an alias of this type on Android.
                    setState { copy(isSaving = false, hasSaveError = true) }
                }
            }
        }

        /** Today keeps the current time of day; another day is noon local time. */
        private fun timestampOf(date: LocalDate): Long =
            if (date == LocalDate.now(clock)) {
                clock.millis()
            } else {
                date
                    .atTime(NOON, 0)
                    .atZone(clock.zone)
                    .toInstant()
                    .toEpochMilli()
            }

        private fun TransactionType.toCategoryType() =
            if (this == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

        companion object {
            const val MAX_NOTE_LENGTH = 200
            private const val NOON = 12
            private val CATEGORY_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
        }
    }
