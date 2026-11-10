package io.github.toanhp123.finote.domain.transaction.model

/** What the user last picked in manual entry: the account, and the category per type (`EXPENSE`, `INCOME`). */
data class ManualChoices(
    val accountId: String?,
    val categoryIds: Map<TransactionType, String?>,
)
