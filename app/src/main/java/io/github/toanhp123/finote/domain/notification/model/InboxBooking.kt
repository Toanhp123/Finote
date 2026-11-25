package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * What the user entered to book inbox item [itemId] (the id of its representative log). [logIds] are the logs the
 * user saw in the item (`FailedInboxItem.logIds`); the booking is refused when any of them was handled meanwhile.
 * [amount] is VND and [timestamp] epoch ms. [resolvedAccountId] is the account the prefill found for the item; the
 * proven-covered check runs only when [accountId] is that account. [merchantName] is the shop name the row keeps.
 */
data class InboxBooking(
    val itemId: String,
    val logIds: List<String>,
    val amount: Long,
    val type: TransactionType,
    val accountId: String,
    val categoryId: String?,
    val note: String,
    val merchantName: String?,
    val timestamp: Long,
    val resolvedAccountId: String?,
) {
    init {
        require(itemId in logIds) { "the item's own log is one of its logs" }
        require(type == TransactionType.EXPENSE || type == TransactionType.INCOME) {
            "an inbox item books a spend or income"
        }
    }

    // Amount, account and note stay out of logs and crash reports.
    override fun toString() = "InboxBooking(itemId=$itemId, type=$type)"
}
