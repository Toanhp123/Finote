package io.github.toanhp123.finote.domain.notification.error

import io.github.toanhp123.finote.domain.transaction.error.TransactionError

/**
 * Why a settlement write was refused: the data it was planned from went stale, or the ledger rules refused the row
 * ([Refused], which a retry cannot fix). The log stays `PENDING`.
 */
sealed interface SettlementError {
    /** The target account is gone, deleted, archived or ignored. */
    data object AccountUnusable : SettlementError

    /** A log of the decision is no longer `PENDING` (another run settled it). */
    data object LogNotPending : SettlementError

    /** The ledger rules refused the booking (for example a post time outside the allowed range). */
    data class Refused(
        val error: TransactionError,
    ) : SettlementError
}
