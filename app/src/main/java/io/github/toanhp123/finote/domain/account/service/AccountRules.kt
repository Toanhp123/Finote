package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection
import io.github.toanhp123.finote.domain.account.model.Account

/** Pure account rules. `null` means allowed. */
object AccountRules {
    fun checkNew(account: Account): AccountError? =
        when {
            account.name.isBlank() -> AccountError.BlankName
            account.currency != "VND" -> AccountError.UnsupportedCurrency
            else -> null
        }

    /** Currency is not editable, so only the name is checked. */
    fun checkUpdate(account: Account): AccountError? = if (account.name.isBlank()) AccountError.BlankName else null

    /** [liveTransactions]: live rows where the account is source or transfer destination. */
    fun checkDelete(liveTransactions: Int): AccountError? =
        if (liveTransactions > 0) AccountError.HasTransactions else null

    /**
     * [source] and [target] are the stored rows found by id, null when missing. [transfersBetween] counts transfers in
     * either direction, deleted ones included. Both rows are one real bank account, so the bank key must match.
     */
    fun checkMerge(
        sourceId: String,
        targetId: String,
        source: Account?,
        target: Account?,
        transfersBetween: Int,
    ): MergeRejection? =
        when {
            sourceId == targetId -> {
                MergeRejection.SAME_ACCOUNT
            }

            source == null || target == null || source.deletedAt != null || target.deletedAt != null -> {
                MergeRejection.ACCOUNT_GONE
            }

            source.bankPackageName == null || source.bankPackageName != target.bankPackageName -> {
                MergeRejection.DIFFERENT_BANK
            }

            transfersBetween > 0 -> {
                MergeRejection.HAS_TRANSFER
            }

            else -> {
                null
            }
        }
}
