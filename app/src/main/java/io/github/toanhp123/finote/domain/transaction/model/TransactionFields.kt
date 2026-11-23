package io.github.toanhp123.finote.domain.transaction.model

/** The form fields that must fit together before a [Transaction] is built. Amounts are VND. */
data class TransactionFields(
    val amount: Long,
    val feeAmount: Long,
    val type: TransactionType,
    val accountId: String,
    val toAccountId: String?,
    val categoryId: String?,
)
