package io.github.toanhp123.finote.ui.transactions

import io.github.toanhp123.finote.domain.transaction.model.ReimbursementStatus
import io.github.toanhp123.finote.domain.transaction.model.Transaction

/** What the reimbursement sheet offers. Each choice is one stored pair of the excluded flag and the status. */
enum class ReimbursementChoice(
    val isExcludedFromAnalytics: Boolean,
    val status: ReimbursementStatus,
) {
    NORMAL(false, ReimbursementStatus.NONE),
    PENDING(true, ReimbursementStatus.PENDING),
    SETTLED(true, ReimbursementStatus.SETTLED),
}

/** The status decides: an odd pair (excluded with NONE, or a status without the flag) reads by its status. */
fun Transaction.reimbursementChoice(): ReimbursementChoice =
    when (reimbursementStatus) {
        ReimbursementStatus.NONE -> ReimbursementChoice.NORMAL
        ReimbursementStatus.PENDING -> ReimbursementChoice.PENDING
        ReimbursementStatus.SETTLED -> ReimbursementChoice.SETTLED
    }

fun ReimbursementChoice.applyTo(transaction: Transaction): Transaction =
    transaction.copy(isExcludedFromAnalytics = isExcludedFromAnalytics, reimbursementStatus = status)
