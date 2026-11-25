package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.notification.model.AdjustmentToken
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.logic.AmountResult
import io.github.toanhp123.finote.ui.cashentry.logic.CalcKey
import io.github.toanhp123.finote.ui.cashentry.logic.ReimbursementChoice
import java.time.LocalDate

/**
 * [accounts] are live and not archived, by sortOrder. [categories] are live and not hidden, of the current [type].
 * [selectedCategoryIds] keeps one choice per type for the whole session.
 */
data class CashEntryState(
    val date: LocalDate,
    /** Whether [date] is today by the ViewModel clock; the sheet shows the word "Today" for it. */
    val isToday: Boolean = true,
    val type: TransactionType = TransactionType.EXPENSE,
    val expression: String = "",
    val result: AmountResult = AmountResult.Empty,
    val accounts: List<Account> = emptyList(),
    val selectedAccountId: String? = null,
    val categories: List<Category> = emptyList(),
    val selectedCategoryIds: Map<TransactionType, String?> = emptyMap(),
    /** Edit mode: the row's own category, offered and kept even when hidden. Null in every other mode. */
    val keptCategoryId: String? = null,
    val note: String = "",
    /** The transfer destination, kept across a type change. Never equals [selectedAccountId]. */
    val destinationId: String? = null,
    /** The transfer fee in VND, kept across a type change; only a transfer saves it. */
    val fee: Long = 0,
    val page: CashEntryPage = CashEntryPage.KEYPAD,
    val isSaving: Boolean = false,
    val saveError: CashEntrySaveError? = null,
    /** False once the sheet should leave, after a save or a dismiss; the screen closes when the exit ends. */
    val isVisible: Boolean = true,
    /** True when the sheet edits a stored transaction instead of adding one. */
    val isEditing: Boolean = false,
    /** True in edit mode until the stored row filled the form. */
    val isLoading: Boolean = false,
    /** Only an expense keeps it; the use case resets it for any other type. */
    val reimbursement: ReimbursementChoice = ReimbursementChoice.NORMAL,
    val isDeleteConfirmOpen: Boolean = false,
    /** Edit mode: original rows were folded into this one by auto-pairing, whatever its type is now. Read once. */
    val isAutoPaired: Boolean = false,
    val isUnpairConfirmOpen: Boolean = false,
    /** From-log mode: the notification the form books, shown above it. Null in every other mode. */
    val sourceText: SourceText? = null,
    /** The adjustment the user is asked to confirm; the dialog shows while it is set. */
    val pendingConfirmation: AdjustmentToken? = null,
    /** An adjustment of the user already includes the notification; the sheet says so and offers Dismiss. */
    val isCoveredNotice: Boolean = false,
    /** From-log mode: the archived account the notification belongs to, named under an empty account field. */
    val archivedAccountName: String? = null,
    /** From-log mode with no valid time in the notification: Save waits until the user picks a date. */
    val isDateMissing: Boolean = false,
) {
    /** The accounts a transfer can go to: every live account except the source. */
    val destinationChoices: List<Account> get() = accounts.filter { it.id != selectedAccountId }

    val isSaveEnabled: Boolean
        get() =
            result is AmountResult.Ok &&
                selectedAccountId != null &&
                !isSaving &&
                !isDateMissing &&
                (type != TransactionType.TRANSFER || destinationId != null)
}

sealed interface CashEntryIntent {
    data class KeyPressed(
        val key: CalcKey,
    ) : CashEntryIntent

    data class TypeChanged(
        val type: TransactionType,
    ) : CashEntryIntent

    data class AccountPicked(
        val id: String,
    ) : CashEntryIntent

    data class DestinationPicked(
        val id: String,
    ) : CashEntryIntent

    /** The raw fee text; the ViewModel keeps its digits only. */
    data class FeeChanged(
        val text: String,
    ) : CashEntryIntent

    /** A null [id] clears the category. */
    data class CategoryPicked(
        val id: String?,
    ) : CashEntryIntent

    data class DatePicked(
        val date: LocalDate,
    ) : CashEntryIntent

    data class NoteChanged(
        val note: String,
    ) : CashEntryIntent

    data class PageOpened(
        val page: CashEntryPage,
    ) : CashEntryIntent

    data object PageClosed : CashEntryIntent

    data object SaveClicked : CashEntryIntent

    data object Dismissed : CashEntryIntent

    data class ReimbursementPicked(
        val choice: ReimbursementChoice,
    ) : CashEntryIntent

    data object DeleteClicked : CashEntryIntent

    data object DeleteConfirmed : CashEntryIntent

    data object DeleteDismissed : CashEntryIntent

    data object UnpairClicked : CashEntryIntent

    data object UnpairConfirmed : CashEntryIntent

    data object UnpairDismissed : CashEntryIntent

    /** The user agreed to book although an adjustment may already include it. */
    data object AdjustmentConfirmed : CashEntryIntent

    data object AdjustmentDismissed : CashEntryIntent

    /** "Already entered": the covered notice's action. */
    data object DismissFromCovered : CashEntryIntent
}

sealed interface CashEntryEffect {
    /** The sheet was never shown, so the Activity must finish without the exit animation. */
    data object Close : CashEntryEffect

    /** From-log mode: the notification was handled meanwhile; the host says so and the sheet leaves. */
    data object ShowAlreadyHandled : CashEntryEffect
}

/** The title and text of the notification a from-log sheet books. */
data class SourceText(
    val title: String,
    val content: String,
) {
    // The notification text stays out of logs and crash reports.
    override fun toString() = "SourceText"
}

/** Why the last save failed; [ACCOUNT_UNUSABLE] means an account in the transaction is archived or ignored. */
enum class CashEntrySaveError { GENERIC, ACCOUNT_UNUSABLE }

/** The content the cash-entry sheet shows. Only [KEYPAD] shows the calculator keypad. */
enum class CashEntryPage { KEYPAD, ACCOUNT, DESTINATION, CATEGORY, FEE, DATE, NOTE }
