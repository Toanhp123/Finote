package io.github.toanhp123.finote.ui.cashentry

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.ui.cashentry.component.reimbursementChoice
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * The form of the stored [row], with [accountList] and [offered] as the live lists. [category] is the row's category
 * when it is still offered, else null. A destination that left the live list shows as none.
 */
internal fun CashEntryState.filledFrom(
    row: Transaction,
    accountList: List<Account>,
    offered: List<Category>,
    category: String?,
    clock: Clock,
): CashEntryState {
    val date = Instant.ofEpochMilli(row.timestamp).atZone(clock.zone).toLocalDate()
    return copy(
        accounts = accountList,
        categories = offered,
        isLoading = false,
        expression = row.amount.toString(),
        result = AmountResult.Ok(row.amount),
        type = row.type,
        selectedAccountId = row.accountId,
        destinationId = row.toAccountId?.takeIf { d -> accountList.any { it.id == d } },
        fee = row.feeAmount,
        selectedCategoryIds = mapOf(row.type to category),
        note = row.note,
        date = date,
        isToday = date == LocalDate.now(clock),
        reimbursement = row.reimbursementChoice(),
    )
}
