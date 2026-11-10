package io.github.toanhp123.finote.domain.account.error

/** Thrown by `AccountRepository.merge` when a check fails. The UI maps [reason] to a message. */
class AccountMergeRejectedException(
    val reason: MergeRejection,
) : IllegalArgumentException(reason.name)
