package io.github.toanhp123.finote.domain.transaction.error

/** Why a transaction change was refused. Decided in the domain, before anything is written. */
sealed interface TransactionError {
    /** Missing or soft-deleted. */
    data object NotFound : TransactionError

    /** Before 2000-01-01 or more than 366 days ahead. */
    data object InvalidTimestamp : TransactionError

    /** Missing, deleted, archived, or ignored. */
    data object AccountUnusable : TransactionError

    data object CategoryNotFound : TransactionError

    /** An expense needs an expense category, an income needs an income category. */
    data object CategoryTypeMismatch : TransactionError

    /** The balance math left the `Long` range. Nothing was written. */
    data object AmountOverflow : TransactionError
}
