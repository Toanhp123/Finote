package io.github.toanhp123.finote.domain.account.error

import io.github.toanhp123.finote.domain.account.model.Account

/** Why an account change was refused. Decided in the domain, before anything is written. */
sealed interface AccountError {
    /** Missing or soft-deleted. */
    data object NotFound : AccountError

    data object BlankName : AccountError

    /** Currency is not "VND". */
    data object UnsupportedCurrency : AccountError

    /** Delete of an account that a live transaction still touches. */
    data object HasTransactions : AccountError

    /** The seeded cash account cannot be deleted. */
    data object SystemAccount : AccountError

    /** Archiving would leave no account that can take transactions. */
    data object LastUsableAccount : AccountError

    data object Archived : AccountError

    /** Archived, ignored or deleted: cannot be starred. */
    data object Unusable : AccountError

    data class MergeRejected(
        val reason: MergeRejection,
    ) : AccountError

    /** A masked number needs a bank app, and this account has none (cash, or no bank package yet). */
    data object NoBankApp : AccountError

    /** A masked number is not exactly four digits. */
    data object InvalidMask : AccountError

    /**
     * A live [holder] already owns the key. [mergeRejection] is null when merging into [holder] is allowed, else the
     * rule that blocks it.
     */
    data class IdentifierTaken(
        val holder: Account,
        val mergeRejection: MergeRejection?,
    ) : AccountError {
        // Only the id: the holder's name and balances stay out of logs and crash reports.
        override fun toString() = "IdentifierTaken(holderId=${holder.id}, mergeRejection=$mergeRejection)"
    }
}
