package io.github.toanhp123.finote.domain.notification.model

import io.github.toanhp123.finote.domain.transaction.error.TransactionError

/** How booking an inbox item ended. Only [Booked] writes anything. */
sealed interface BookInboxResult {
    data object Booked : BookInboxResult

    /** A log of the item is no longer a failed inbox log (booked, dismissed or re-parsed meanwhile). */
    data object AlreadyHandled : BookInboxResult

    /** An adjustment the user made already includes this log; the item stays in the inbox. */
    data object CoveredByAdjustment : BookInboxResult

    /** A later adjustment may include this log; ask, then book again with [token]. */
    data class NeedsAdjustmentConfirmation(
        val token: AdjustmentToken,
    ) : BookInboxResult

    /** The ledger rules refused the entry. */
    data class Refused(
        val error: TransactionError,
    ) : BookInboxResult
}
