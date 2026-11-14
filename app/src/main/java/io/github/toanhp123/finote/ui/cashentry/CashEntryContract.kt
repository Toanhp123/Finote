package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
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
    val note: String = "",
    val page: CashEntryPage = CashEntryPage.KEYPAD,
    val isSaving: Boolean = false,
    val hasSaveError: Boolean = false,
    /** False once the sheet should leave, after a save or a dismiss; the screen closes when the exit ends. */
    val isVisible: Boolean = true,
) {
    val isSaveEnabled: Boolean get() = result is AmountResult.Ok && selectedAccountId != null && !isSaving
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
}

/** This screen sends no one-time command; the type exists for the contract rule and [BaseViewModel]. */
sealed interface CashEntryEffect

/** The content the cash-entry sheet shows. Only [KEYPAD] shows the calculator keypad. */
enum class CashEntryPage { KEYPAD, ACCOUNT, CATEGORY, DATE, NOTE }
