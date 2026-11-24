package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.SYSTEM_CASH_ACCOUNT_ID
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType

/** Picks the account and category the cash-entry sheet preselects. */
object CashEntryDefaults {
    /** Usable accounts only. Picks the starred one, else last used, else cash, else lowest sortOrder. */
    fun pickAccount(
        accounts: List<Account>,
        lastUsedId: String?,
    ): String? {
        val live = accounts.filter { it.isUsable }
        val picked =
            live.firstOrNull { it.isPrimary }
                ?: live.firstOrNull { it.id == lastUsedId }
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
