package io.github.toanhp123.finote.ui.cashentry.logic

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxBooking
import io.github.toanhp123.finote.domain.notification.model.InboxPrefill
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.ui.cashentry.CashEntryState
import io.github.toanhp123.finote.ui.cashentry.SourceText
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

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

/** The inbox [item] a from-log sheet books and the [prefill] made of it. */
internal class InboxSource(
    val item: FailedInboxItem,
    val prefill: InboxPrefill,
) {
    // Text, amount and account stay out of logs and crash reports.
    override fun toString() = "InboxSource"
}

/**
 * The form of a from-log sheet: [InboxSource.prefill] as it is (an empty account stays empty, an unknown amount stays
 * empty), with [accountList] and [offered] as the live lists. The category is the rule's suggestion when it is
 * offered, else none. No reimbursement is preselected.
 * A notification without a valid time shows today and waits for the user to pick the date.
 */
internal fun CashEntryState.filledFromInbox(
    source: InboxSource,
    accountList: List<Account>,
    offered: List<Category>,
    clock: Clock,
): CashEntryState {
    val prefill = source.prefill
    val date = prefill.timestamp?.let { Instant.ofEpochMilli(it).atZone(clock.zone).toLocalDate() }
    val amount = prefill.amount
    val suggested = prefill.categoryId?.takeIf { id -> offered.any { it.id == id } }
    return copy(
        accounts = accountList,
        categories = offered,
        isLoading = false,
        type = prefill.type,
        expression = amount?.toString().orEmpty(),
        result = if (amount == null) AmountResult.Empty else AmountResult.Ok(amount),
        selectedAccountId = prefill.accountId?.takeIf { id -> accountList.any { it.id == id } },
        selectedCategoryIds = suggested?.let { mapOf(prefill.type to it) }.orEmpty(),
        note = prefill.note,
        date = date ?: LocalDate.now(clock),
        isToday = date == LocalDate.now(clock),
        isDateMissing = date == null,
        sourceText = SourceText(source.item.title, source.item.content),
        archivedAccountName = prefill.archivedAccountName,
    )
}

/**
 * What the booking stores as time: exactly [logTime] while [date] is still its day, else [date] at the time of day of
 * [logTime] in [zone] (noon when the log has no valid time). It never goes through the manual entry's "now for today,
 * noon otherwise", which would move a missed morning spend behind an adjustment that already counts it.
 */
internal fun inboxTimestamp(
    logTime: Long?,
    date: LocalDate,
    zone: ZoneId,
): Long {
    val at = logTime?.let { Instant.ofEpochMilli(it).atZone(zone) }
    if (logTime != null && at?.toLocalDate() == date) return logTime
    return date
        .atTime(at?.toLocalTime() ?: LocalTime.NOON)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}

/**
 * The booking the user entered for [source], or null while the form cannot be saved. The logs are the ones the sheet
 * showed, so a copy handled meanwhile is caught.
 */
internal fun CashEntryState.toInboxBooking(
    source: InboxSource,
    zone: ZoneId,
): InboxBooking? {
    val amount = (result as? AmountResult.Ok)?.amount
    val accountId = selectedAccountId
    val isBookable = isSaveEnabled && (type == TransactionType.EXPENSE || type == TransactionType.INCOME)
    if (!isBookable || amount == null || accountId == null) return null
    return InboxBooking(
        itemId = source.item.id,
        logIds = source.item.logIds,
        amount = amount,
        type = type,
        accountId = accountId,
        categoryId = selectedCategoryIds[type],
        note = note,
        merchantName = source.prefill.merchantName,
        timestamp = inboxTimestamp(source.prefill.timestamp, date, zone),
        resolvedAccountId = source.prefill.accountId,
    )
}
