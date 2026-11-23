package io.github.toanhp123.finote.domain.transaction.model

import java.time.LocalDate

/** What the edit form submits for an existing transaction. The time of day stays with the stored row. */
data class TransactionEdit(
    val fields: TransactionFields,
    val date: LocalDate,
    val note: String,
    val isExcludedFromAnalytics: Boolean,
    val reimbursementStatus: ReimbursementStatus,
)
