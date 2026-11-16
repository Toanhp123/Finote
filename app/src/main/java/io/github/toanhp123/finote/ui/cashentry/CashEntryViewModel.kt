package io.github.toanhp123.finote.ui.cashentry

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionEdit
import io.github.toanhp123.finote.domain.transaction.model.TransactionFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import io.github.toanhp123.finote.ui.cashentry.logic.AmountExpression
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.CashEntryDefaults
import io.github.toanhp123.finote.ui.cashentry.logic.filledFrom
import io.github.toanhp123.finote.ui.common.BaseViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

@HiltViewModel(assistedFactory = CashEntryViewModel.Factory::class)
class CashEntryViewModel
    @Suppress("LongParameterList") // why: Hilt injects one collaborator per use case; grouping them would hide the deps
    @AssistedInject
    constructor(
        // Null: the sheet adds a transaction. Otherwise it edits this one.
        @Assisted private val transactionId: String?,
        private val observeUsableAccounts: ObserveUsableAccountsUseCase,
        private val observeCategories: ObserveCategoriesUseCase,
        private val getLastManualChoices: GetLastManualChoicesUseCase,
        private val addManualTransaction: AddManualTransactionUseCase,
        private val getTransaction: GetTransactionUseCase,
        private val updateTransaction: UpdateTransactionUseCase,
        private val deleteTransaction: DeleteTransactionUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<CashEntryState, CashEntryIntent, CashEntryEffect>(
            CashEntryState(
                date = LocalDate.now(clock),
                isEditing = transactionId != null,
                isLoading = transactionId != null,
            ),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(transactionId: String?): CashEntryViewModel
        }

        private var lastAccountId: String? = null
        private var isSeeded = false
        private var allCategories: List<Category> = emptyList()

        init {
            viewModelScope.launch {
                // The first read is inside the flow, so a failure reaches catch; the lists then stay empty.
                combine(
                    flow { emit(readStart()) },
                    observeUsableAccounts(),
                    observeCategories(),
                ) { start, a, c -> Triple(start, a, c) }
                    .catchStorage {
                        // Save stays disabled with no accounts. Nothing is logged: accounts and notes are private.
                        if (state.value.isLoading) sendEffect(CashEntryEffect.Close)
                    }.collect { (start, a, c) -> onLists(start, a, c) }
            }
        }

        /** Create mode reads the last choices, edit mode the stored row. Read once, kept by the combine. */
        private suspend fun readStart(): Start =
            if (transactionId == null) {
                Start(getLastManualChoices(), null)
            } else {
                Start(ManualChoices(null, emptyMap()), getTransaction(transactionId))
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

                is CashEntryIntent.DestinationPicked -> {
                    onDestination(intent.id)
                }

                is CashEntryIntent.FeeChanged -> {
                    setState { copy(fee = feeOf(intent.text), hasSaveError = false) }
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
                    setState { copy(isVisible = false) }
                }

                is CashEntryIntent.ReimbursementPicked -> {
                    setState { copy(reimbursement = intent.choice).backToKeypad() }
                }

                CashEntryIntent.DeleteClicked, CashEntryIntent.DeleteConfirmed, CashEntryIntent.DeleteDismissed -> {
                    onDelete(intent)
                }
            }
        }

        // Only PageOpened and PageClosed reach here: any other intent counts as a close.
        private fun onPage(intent: CashEntryIntent) =
            setState { if (intent is CashEntryIntent.PageOpened) copy(page = intent.page) else backToKeypad() }

        // An id that is not in the live list (for example archived while the page was open) changes nothing.
        // A destination equal to the new source is cleared, so the two never match.
        private fun onAccount(id: String) =
            setState {
                if (accounts.any { it.id == id }) {
                    copy(selectedAccountId = id, destinationId = destinationId?.takeIf { it != id }).backToKeypad()
                } else {
                    this
                }
            }

        // Only a live account other than the source counts; anything else changes nothing.
        private fun onDestination(id: String) =
            setState { if (destinationChoices.any { it.id == id }) copy(destinationId = id).backToKeypad() else this }

        // Digits only, at most MAX_DIGITS: the fee fits a Long, and the use case checks amount + fee.
        private fun feeOf(text: String): Long =
            text
                .filter { it in '0'..'9' }
                .take(AmountExpression.MAX_DIGITS)
                .toLongOrNull() ?: 0L

        private fun CashEntryState.backToKeypad() = copy(page = CashEntryPage.KEYPAD)

        private fun CashEntryState.pickCategory(id: String?) =
            copy(selectedCategoryIds = selectedCategoryIds + (type to id))

        private fun onKey(intent: CashEntryIntent.KeyPressed) =
            setState {
                val next = AmountExpression.press(expression, intent.key)
                copy(expression = next, result = AmountExpression.evaluate(next), hasSaveError = false)
            }

        private fun onLists(
            start: Start,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            allCategories = categoryList
            when {
                transactionId == null -> onCreateLists(start.choices, accountList, categoryList)
                state.value.isLoading -> onFirstEditLists(start.row, accountList, categoryList)
                else -> onLaterEditLists(accountList, categoryList)
            }
        }

        // The row fills the form once. A row that cannot be edited, or whose account is gone, closes the screen.
        private fun onFirstEditLists(
            row: Transaction?,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            if (row == null || row.type == TransactionType.ADJUSTMENT || accountList.none { it.id == row.accountId }) {
                sendEffect(CashEntryEffect.Close)
                return
            }
            val category = CashEntryDefaults.pickCategory(categoryList, row.categoryId, row.type.toCategoryType())
            setState { filledFrom(row, accountList, visible(row.type), category, clock) }
        }

        // Never picks another account: a source that left the list ends the edit. Close is sent on the visible to
        // hidden step only, so it goes out once; the sheet may never report "settled hidden" itself.
        private fun onLaterEditLists(
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            if (state.value.isVisible && accountList.none { it.id == state.value.selectedAccountId }) {
                sendEffect(CashEntryEffect.Close)
            }
            setState {
                copy(
                    accounts = accountList,
                    isVisible = isVisible && accountList.any { it.id == selectedAccountId },
                    destinationId =
                        destinationId?.takeIf { d -> d != selectedAccountId && accountList.any { it.id == d } },
                    selectedCategoryIds =
                        selectedCategoryIds.mapValues { (t, id) ->
                            CashEntryDefaults.pickCategory(categoryList, id, t.toCategoryType())
                        },
                    categories = visible(type),
                )
            }
        }

        private fun onCreateLists(
            choices: ManualChoices,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
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
                    destinationId = destinationId?.takeIf { d -> d != account && accountList.any { it.id == d } },
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
            // Destination and fee belong to a transfer only; a transfer has no category.
            val isTransfer = s.type == TransactionType.TRANSFER
            val categoryId = if (isTransfer) null else s.selectedCategoryIds[s.type]
            val toAccountId = if (isTransfer) s.destinationId else null
            val feeAmount = if (isTransfer) s.fee else 0L
            launchCatching(onStorageError = ::showSaveError) {
                val result =
                    if (transactionId == null) {
                        addManualTransaction(
                            amount,
                            s.type,
                            accountId,
                            categoryId,
                            s.date,
                            s.note,
                            toAccountId,
                            feeAmount,
                        )
                    } else {
                        val fields = TransactionFields(amount, feeAmount, s.type, accountId, toAccountId, categoryId)
                        val edit =
                            TransactionEdit(
                                fields,
                                s.date,
                                s.note,
                                s.reimbursement.isExcludedFromAnalytics,
                                s.reimbursement.status,
                            )
                        updateTransaction(transactionId, edit)
                    }
                when (result) {
                    // isSaving stays true so a second save cannot run while the sheet leaves.
                    is Outcome.Ok -> setState { copy(isVisible = false) }

                    is Outcome.Err -> showSaveError()
                }
            }
        }

        // Only the three delete intents reach here. They do nothing in create mode or while a save runs.
        private fun onDelete(intent: CashEntryIntent) {
            if (transactionId == null || state.value.isSaving) return
            when (intent) {
                CashEntryIntent.DeleteClicked -> setState { copy(isDeleteConfirmOpen = true) }
                CashEntryIntent.DeleteConfirmed -> delete(transactionId)
                else -> setState { copy(isDeleteConfirmOpen = false) }
            }
        }

        private fun delete(id: String) {
            setState { copy(isDeleteConfirmOpen = false, isSaving = true, hasSaveError = false) }
            launchCatching(onStorageError = ::showSaveError) {
                when (deleteTransaction(id)) {
                    is Outcome.Ok -> setState { copy(isVisible = false) }
                    is Outcome.Err -> showSaveError()
                }
            }
        }

        private fun showSaveError() = setState { copy(isSaving = false, hasSaveError = true) }

        private fun TransactionType.toCategoryType() =
            if (this == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

        /** What the first read gives: the last choices in create mode, the stored row (null if gone) in edit mode. */
        private class Start(
            val choices: ManualChoices,
            val row: Transaction?,
        )

        companion object {
            const val MAX_NOTE_LENGTH = 200
            private val CATEGORY_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME)
        }
    }
