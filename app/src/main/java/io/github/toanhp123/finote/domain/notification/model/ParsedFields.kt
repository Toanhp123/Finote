package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/** What a rule read from a notification. [type] is INCOME or EXPENSE. Money is VND. */
data class ParsedFields(
    val amount: Long,
    val type: TransactionType,
    val accountMask: String?,
    val postBalance: Long?,
    val merchant: String?,
    val note: String?,
)
