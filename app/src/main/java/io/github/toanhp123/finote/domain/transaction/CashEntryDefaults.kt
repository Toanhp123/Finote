package io.github.toanhp123.finote.domain.transaction

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType

/** Picks the account and category the cash-entry sheet preselects. */
object CashEntryDefaults {
    /** Live (not deleted, not archived) accounts only: last used, else cash, else lowest sortOrder, else null. */
    fun pickAccount(
        accounts: List<Account>,
        lastUsedId: String?,
    ): String? {
        val live = accounts.filter { it.deletedAt == null && !it.isArchived }
        val picked =
            live.firstOrNull { it.id == lastUsedId }
                ?: live.firstOrNull { it.id == SYSTEM_CASH_ACCOUNT_ID }
                ?: live.minByOrNull { it.sortOrder }
        return picked?.id
    }

    /** Null unless the id is a live, not hidden category whose type matches. */
    fun pickCategory(
        categories: List<Category>,
        lastUsedId: String?,
        type: CategoryType,
    ): String? =
        categories
            .firstOrNull {
                it.id == lastUsedId && it.type == type && it.deletedAt == null && !it.isHidden
            }?.id
}
