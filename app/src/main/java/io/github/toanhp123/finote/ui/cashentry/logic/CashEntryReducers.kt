package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.notification.model.BookInboxResult
import io.github.toanhp123.finote.domain.transaction.error.TransactionError
import io.github.toanhp123.finote.domain.transaction.model.ManualChoices
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntrySaveError
import io.github.toanhp123.finote.ui.cashentry.CashEntryState

internal fun TransactionType.toCategoryType() =
    if (this == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE

/** The live categories that are not hidden, of [type], by sortOrder. */
internal fun List<Category>.offeredFor(type: TransactionType): List<Category> =
    filter { it.type == type.toCategoryType() && it.deletedAt == null && !it.isHidden }.sortedBy { it.sortOrder }

private fun Map<TransactionType, String?>.stillIn(categoryList: List<Category>) =
    mapValues { (type, id) -> CashEntryDefaults.pickCategory(categoryList, id, type.toCategoryType()) }

/**
 * Edit mode, after the first fill: the sheet leaves when its source account left [accountList]. A destination that
 * left it, or equals the source, shows as none.
 */
internal fun CashEntryState.withEditLists(
    accountList: List<Account>,
    categoryList: List<Category>,
): CashEntryState =
    copy(
        accounts = accountList,
        isVisible = isVisible && accountList.any { it.id == selectedAccountId },
        destinationId = destinationId?.takeIf { d -> d != selectedAccountId && accountList.any { it.id == d } },
        selectedCategoryIds = selectedCategoryIds.stillIn(categoryList),
        categories = categoryList.offeredFor(type),
    )

/** From-log mode, after the first fill: an account that left [accountList] leaves the field empty, so Save is off. */
internal fun CashEntryState.withInboxLists(
    accountList: List<Account>,
    categoryList: List<Category>,
): CashEntryState =
    copy(
        accounts = accountList,
        selectedAccountId = selectedAccountId?.takeIf { id -> accountList.any { it.id == id } },
        selectedCategoryIds = selectedCategoryIds.stillIn(categoryList),
        categories = categoryList.offeredFor(type),
    )

/**
 * The sheet after a from-log save ended with [result]. A booked or handled item hides the sheet, and `isSaving` stays
 * so a second save cannot run while it leaves.
 */
internal fun CashEntryState.afterBooking(result: BookInboxResult): CashEntryState =
    when (result) {
        BookInboxResult.Booked, BookInboxResult.AlreadyHandled -> copy(isVisible = false)
        BookInboxResult.CoveredByAdjustment -> copy(isSaving = false, isCoveredNotice = true)
        is BookInboxResult.NeedsAdjustmentConfirmation -> copy(isSaving = false, pendingConfirmation = result.token)
        is BookInboxResult.Refused -> copy(isSaving = false, saveError = CashEntrySaveError.GENERIC)
    }

/** Only a refused account has its own text; any other use case error shows the generic one. */
internal fun saveErrorOf(error: TransactionError?): CashEntrySaveError =
    if (error == TransactionError.AccountUnusable) CashEntrySaveError.ACCOUNT_UNUSABLE else CashEntrySaveError.GENERIC

private val CATEGORY_TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME)

/**
 * Create mode, on every list value. [choices] seed the categories on the first value only ([isFirst]); every value
 * validates what is chosen. The account falls back to the last used one, then the default pick.
 */
internal fun CashEntryState.withCreateLists(
    choices: ManualChoices,
    isFirst: Boolean,
    accountList: List<Account>,
    categoryList: List<Category>,
): CashEntryState {
    val chosen = if (isFirst) choices.categoryIds else selectedCategoryIds
    val account =
        selectedAccountId?.takeIf { id -> accountList.any { it.id == id } }
            ?: CashEntryDefaults.pickAccount(accountList, choices.accountId)
    return copy(
        accounts = accountList,
        selectedAccountId = account,
        destinationId = destinationId?.takeIf { d -> d != account && accountList.any { it.id == d } },
        selectedCategoryIds =
            CATEGORY_TYPES.associateWith {
                CashEntryDefaults.pickCategory(
                    categoryList,
                    chosen[it],
                    it.toCategoryType(),
                )
            },
        categories = categoryList.offeredFor(type),
    )
}
