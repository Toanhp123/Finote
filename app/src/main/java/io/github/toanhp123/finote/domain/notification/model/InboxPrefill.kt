package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * What the cash entry sheet starts with for one inbox item. A null [amount] or [timestamp] is empty and required;
 * [accountId] is set only for one live, usable owner; when that owner is archived, [accountId] is null and
 * [archivedAccountName] names it.
 */
data class InboxPrefill(
    val type: TransactionType,
    val amount: Long?,
    val accountId: String?,
    val archivedAccountName: String?,
    val note: String,
    val timestamp: Long?,
) {
    // Amount, account and note stay out of logs and crash reports.
    override fun toString() = "InboxPrefill(type=$type)"
}
