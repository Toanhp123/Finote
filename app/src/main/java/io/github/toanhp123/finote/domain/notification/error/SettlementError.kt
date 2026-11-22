package io.github.toanhp123.finote.domain.notification.error

import io.github.toanhp123.finote.domain.transaction.error.TransactionError

/**
 * Why a settlement write was refused: the data it was planned from went stale, or the ledger rules refused the row
 * ([Refused], which a retry cannot fix). The log stays `PENDING`, except for [AdjustedAfter].
 */
sealed interface SettlementError {
    /** The target account is gone, deleted, archived or ignored. */
    data object AccountUnusable : SettlementError

    /** A log of the decision is no longer `PENDING` (another run settled it). */
    data object LogNotPending : SettlementError

    /**
     * An adjustment the user made already covers this log's account at its time. Final: the log fails and is not
     * retried, unlike the other errors.
     */
    data object AdjustedAfter : SettlementError

    /** The ledger rules refused the booking (for example a post time outside the allowed range). */
    data class Refused(
        val error: TransactionError,
    ) : SettlementError
}
