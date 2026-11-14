package io.github.toanhp123.finote.domain.notification.error

/** Why a settlement write was refused. The data it was planned from went stale; the log stays `PENDING`. */
sealed interface SettlementError {
    /** The target account is gone, deleted, archived or ignored. */
    data object AccountUnusable : SettlementError

    /** A log of the decision is no longer `PENDING` (another run settled it). */
    data object LogNotPending : SettlementError
}
