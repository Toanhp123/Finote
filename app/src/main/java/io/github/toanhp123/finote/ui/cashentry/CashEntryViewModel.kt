package io.github.toanhp123.finote.ui.cashentry

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.usecase.ObserveUsableAccountsUseCase
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.usecase.ObserveCategoriesUseCase
import io.github.toanhp123.finote.domain.common.IsDebuggable
import io.github.toanhp123.finote.domain.common.Outcome
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.notification.usecase.BookInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.DismissInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.GetInboxItemUseCase
import io.github.toanhp123.finote.domain.notification.usecase.GetInboxPrefillUseCase
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionEdit
import io.github.toanhp123.finote.domain.transaction.model.TransactionFields
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.usecase.AddManualTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.DeleteTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetLastManualChoicesUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.GetTransactionUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.IsAutoPairedUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UnpairTransferUseCase
import io.github.toanhp123.finote.domain.transaction.usecase.UpdateTransactionUseCase
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.CashEntryDefaults
import io.github.toanhp123.finote.ui.cashentry.logic.InboxSource
import io.github.toanhp123.finote.ui.cashentry.logic.afterBooking
import io.github.toanhp123.finote.ui.cashentry.logic.afterKey
import io.github.toanhp123.finote.ui.cashentry.logic.backToKeypad
import io.github.toanhp123.finote.ui.cashentry.logic.busy
import io.github.toanhp123.finote.ui.cashentry.logic.feeOf
import io.github.toanhp123.finote.ui.cashentry.logic.filledFrom
import io.github.toanhp123.finote.ui.cashentry.logic.filledFromInbox
import io.github.toanhp123.finote.ui.cashentry.logic.offeredFor
import io.github.toanhp123.finote.ui.cashentry.logic.saveErrorOf
import io.github.toanhp123.finote.ui.cashentry.logic.toCategoryType
import io.github.toanhp123.finote.ui.cashentry.logic.toInboxBooking
import io.github.toanhp123.finote.ui.cashentry.logic.withAccount
import io.github.toanhp123.finote.ui.cashentry.logic.withCategory
import io.github.toanhp123.finote.ui.cashentry.logic.withCreateLists
import io.github.toanhp123.finote.ui.cashentry.logic.withDestination
import io.github.toanhp123.finote.ui.cashentry.logic.withEditLists
import io.github.toanhp123.finote.ui.cashentry.logic.withInboxLists
import io.github.toanhp123.finote.ui.cashentry.logic.withoutConfirms
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
        @Assisted("transaction") private val transactionId: String?,
        // Not null: the sheet books this failed inbox item (from-log mode). Then [transactionId] is null.
        @Assisted("inbox") private val inboxItemId: String?,
        private val observeUsableAccounts: ObserveUsableAccountsUseCase,
        private val observeCategories: ObserveCategoriesUseCase,
        private val getLastManualChoices: GetLastManualChoicesUseCase,
        private val addManualTransaction: AddManualTransactionUseCase,
        private val getTransaction: GetTransactionUseCase,
        private val updateTransaction: UpdateTransactionUseCase,
        private val deleteTransaction: DeleteTransactionUseCase,
        private val unpairTransfer: UnpairTransferUseCase,
        private val isAutoPaired: IsAutoPairedUseCase,
        private val getInboxItem: GetInboxItemUseCase,
        private val getInboxPrefill: GetInboxPrefillUseCase,
        private val bookInboxItem: BookInboxItemUseCase,
        private val dismissInboxItem: DismissInboxItemUseCase,
        private val clock: Clock,
        @IsDebuggable isDebuggable: Boolean,
    ) : BaseViewModel<CashEntryState, CashEntryIntent, CashEntryEffect>(
            CashEntryState(
                date = LocalDate.now(clock),
                isEditing = transactionId != null,
                isLoading = transactionId != null || inboxItemId != null,
            ),
            isDebuggable,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(
                @Assisted("transaction") transactionId: String?,
                @Assisted("inbox") inboxItemId: String? = null,
            ): CashEntryViewModel
        }

        private var isSeeded = false
        private var source: InboxSource? = null
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
            when {
                inboxItemId != null -> Start(NO_CHOICES, null, inbox = readInbox(inboxItemId))
                transactionId == null -> Start(getLastManualChoices(), null)
                else -> Start(NO_CHOICES, getTransaction(transactionId), isAutoPaired(transactionId))
            }

        // Null when the item was handled since the inbox showed it.
        private suspend fun readInbox(id: String): InboxSource? {
            val item = getInboxItem(id) ?: return null
            return getInboxPrefill(id)?.let { InboxSource(item, it) }
        }

        override fun onIntent(intent: CashEntryIntent) {
            when (intent) {
                is CashEntryIntent.KeyPressed -> {
                    setState { afterKey(intent.key) }
                }

                is CashEntryIntent.TypeChanged -> {
                    setState { copy(type = intent.type, categories = visible(intent.type)) }
                }

                is CashEntryIntent.AccountPicked -> {
                    setState { withAccount(intent.id) }
                }

                is CashEntryIntent.DestinationPicked -> {
                    setState { withDestination(intent.id) }
                }

                is CashEntryIntent.FeeChanged -> {
                    setState { copy(fee = feeOf(intent.text), saveError = null) }
                }

                is CashEntryIntent.CategoryPicked -> {
                    setState { withCategory(intent.id) }
                }

                is CashEntryIntent.DatePicked -> {
                    setState {
                        copy(date = intent.date, isToday = intent.date == LocalDate.now(clock), isDateMissing = false)
                            .backToKeypad()
                    }
                }

                is CashEntryIntent.NoteChanged -> {
                    setState { copy(note = intent.note.take(MAX_NOTE_LENGTH)) }
                }

                // Any intent but PageOpened counts as a close.
                is CashEntryIntent.PageOpened, CashEntryIntent.PageClosed -> {
                    setState { if (intent is CashEntryIntent.PageOpened) copy(page = intent.page) else backToKeypad() }
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

                // Delete and unpair act in edit mode only, the adjustment and dismiss intents in from-log mode only.
                CashEntryIntent.DeleteClicked,
                CashEntryIntent.DeleteConfirmed,
                CashEntryIntent.DeleteDismissed,
                CashEntryIntent.UnpairClicked,
                CashEntryIntent.UnpairConfirmed,
                CashEntryIntent.UnpairDismissed,
                CashEntryIntent.AdjustmentConfirmed,
                CashEntryIntent.AdjustmentDismissed,
                CashEntryIntent.DismissFromCovered,
                -> {
                    onEdit(intent)
                    onInbox(intent)
                }
            }
        }

        private fun onLists(
            start: Start,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            allCategories = categoryList
            when {
                inboxItemId != null -> onInboxLists(start.inbox, accountList, categoryList)
                transactionId == null -> onCreateLists(start.choices, accountList, categoryList)
                state.value.isLoading -> onFirstEditLists(start, accountList, categoryList)
                else -> onLaterEditLists(accountList, categoryList)
            }
        }

        // The row fills the form once. A row that cannot be edited, or whose account is gone, closes the screen.
        private fun onFirstEditLists(
            start: Start,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            val row = start.row
            if (row == null || row.type == TransactionType.ADJUSTMENT || accountList.none { it.id == row.accountId }) {
                sendEffect(CashEntryEffect.Close)
                return
            }
            val category = CashEntryDefaults.pickCategory(categoryList, row.categoryId, row.type.toCategoryType())
            setState {
                filledFrom(row, accountList, visible(row.type), category, clock).copy(isAutoPaired = start.isAutoPaired)
            }
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
            setState { withEditLists(accountList, categoryList) }
        }

        // The item fills the form once. An item that is gone ends the sheet with the "already handled" message.
        private fun onInboxLists(
            inbox: InboxSource?,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            when {
                inbox == null -> {
                    if (state.value.isVisible) {
                        setState { copy(isVisible = false) }
                        sendEffect(CashEntryEffect.ShowAlreadyHandled)
                        sendEffect(CashEntryEffect.Close)
                    }
                }

                state.value.isLoading -> {
                    source = inbox
                    setState { filledFromInbox(inbox, accountList, visible(inbox.prefill.type), clock) }
                }

                else -> {
                    setState { withInboxLists(accountList, categoryList) }
                }
            }
        }

        private fun onCreateLists(
            choices: ManualChoices,
            accountList: List<Account>,
            categoryList: List<Category>,
        ) {
            val isFirst = !isSeeded
            isSeeded = true
            setState { withCreateLists(choices, isFirst, accountList, categoryList) }
        }

        private fun visible(type: TransactionType): List<Category> = allCategories.offeredFor(type)

        private fun save() {
            val s = state.value
            val amount = (s.result as? AmountResult.Ok)?.amount
            val accountId = s.selectedAccountId
            if (!s.isSaveEnabled || amount == null || accountId == null) return
            source?.let { return saveInbox(it, null) }
            // Destination and fee belong to a transfer only; a transfer has no category.
            val isTransfer = s.type == TransactionType.TRANSFER
            val categoryId = if (isTransfer) null else s.selectedCategoryIds[s.type]
            val toAccountId = if (isTransfer) s.destinationId else null
            val feeAmount = if (isTransfer) s.fee else 0L
            leaveAfter {
                if (transactionId == null) {
                    addManualTransaction(amount, s.type, accountId, categoryId, s.date, s.note, toAccountId, feeAmount)
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
            }
        }

        // From-log mode never calls AddManualTransactionUseCase: one use case books the item and settles its logs.
        // [confirmation] is null on a plain Save and the dialog's token after a confirm; the use case checks it
        // against the current account and time and asks again when they differ.
        private fun saveInbox(
            inbox: InboxSource,
            confirmation: AdjustmentToken?,
        ) {
            val booking = state.value.toInboxBooking(inbox, clock.zone) ?: return
            setState { busy().copy(isCoveredNotice = false, pendingConfirmation = null) }
            launchCatching(onStorageError = { showSaveError() }) {
                val result = bookInboxItem(booking, confirmation)
                setState { afterBooking(result) }
                if (result == BookInboxResult.AlreadyHandled) sendEffect(CashEntryEffect.ShowAlreadyHandled)
            }
        }

        // The adjustment and dismiss intents. Anything else, and any intent outside from-log mode or while a save
        // runs, does nothing.
        private fun onInbox(intent: CashEntryIntent) {
            val inbox = source?.takeUnless { state.value.isSaving } ?: return
            when (intent) {
                CashEntryIntent.AdjustmentConfirmed -> state.value.pendingConfirmation?.let { saveInbox(inbox, it) }
                CashEntryIntent.AdjustmentDismissed -> setState { copy(pendingConfirmation = null) }
                CashEntryIntent.DismissFromCovered -> dismissInbox(inbox)
                else -> return
            }
        }

        // Dismissed or already handled, the sheet leaves; only the second says why.
        private fun dismissInbox(inbox: InboxSource) {
            setState { busy() }
            launchCatching(onStorageError = { showSaveError() }) {
                if (!dismissInboxItem(inbox.item.id, inbox.item.logIds)) sendEffect(CashEntryEffect.ShowAlreadyHandled)
                setState { copy(isVisible = false) }
            }
        }

        // Runs [action] with the sheet busy and both confirm dialogs closed: Ok hides the sheet (isSaving stays true so
        // a second save cannot run while it leaves), Err shows the save error.
        private fun leaveAfter(action: suspend () -> Outcome<*, TransactionError>) {
            setState { busy().withoutConfirms() }
            launchCatching(onStorageError = { showSaveError() }) {
                when (val outcome = action()) {
                    is Outcome.Ok -> setState { copy(isVisible = false) }
                    is Outcome.Err -> showSaveError(saveErrorOf(outcome.error))
                }
            }
        }

        // The delete and unpair intents. Anything else, and any intent in create mode or while a save runs, does
        // nothing.
        private fun onEdit(intent: CashEntryIntent) {
            if (transactionId == null || state.value.isSaving) return
            when (intent) {
                CashEntryIntent.DeleteClicked -> setState { copy(isDeleteConfirmOpen = true) }
                CashEntryIntent.DeleteConfirmed -> leaveAfter { deleteTransaction(transactionId) }
                CashEntryIntent.UnpairClicked -> setState { copy(isUnpairConfirmOpen = true) }
                CashEntryIntent.UnpairConfirmed -> leaveAfter { unpairTransfer(transactionId) }
                CashEntryIntent.DeleteDismissed, CashEntryIntent.UnpairDismissed -> setState { withoutConfirms() }
                else -> return
            }
        }

        private fun showSaveError(error: CashEntrySaveError = CashEntrySaveError.GENERIC) =
            setState { copy(isSaving = false, saveError = error) }

        /**
         * What the first read gives: the last choices in create mode, the stored row (null if gone) and whether
         * auto-pairing made it in edit mode, the inbox item and its prefill (null if gone) in from-log mode.
         */
        private class Start(
            val choices: ManualChoices,
            val row: Transaction?,
            val isAutoPaired: Boolean = false,
            val inbox: InboxSource? = null,
        )

        companion object {
            const val MAX_NOTE_LENGTH = 200
            private val NO_CHOICES = ManualChoices(null, emptyMap())
        }
    }
