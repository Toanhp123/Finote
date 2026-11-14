package io.github.toanhp123.finote.domain.account.error

/** Why an account change was refused. Decided in the domain, before anything is written. */
sealed interface AccountError {
    /** Missing or soft-deleted. */
    data object NotFound : AccountError

    data object BlankName : AccountError

    /** Currency is not "VND". */
    data object UnsupportedCurrency : AccountError

    /** Delete of an account that a live transaction still touches. */
    data object HasTransactions : AccountError

    data class MergeRejected(
        val reason: MergeRejection,
    ) : AccountError
}
