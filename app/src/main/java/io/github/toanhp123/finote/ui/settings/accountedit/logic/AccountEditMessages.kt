package io.github.toanhp123.finote.ui.settings.accountedit.logic

import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.error.AccountError
import io.github.toanhp123.finote.domain.account.error.MergeRejection

/** The message for a refused change. [AccountError.IdentifierTaken] takes the holder's name as its argument. */
@StringRes
fun AccountError.messageRes(): Int =
    when (this) {
        AccountError.NotFound -> R.string.account_error_not_found
        AccountError.BlankName -> R.string.account_error_blank_name
        AccountError.UnsupportedCurrency -> R.string.account_error_currency
        AccountError.HasTransactions -> R.string.account_error_has_transactions
        AccountError.SystemAccount -> R.string.account_error_system
        AccountError.LastUsableAccount -> R.string.account_error_last_usable
        AccountError.Archived -> R.string.account_error_archived
        AccountError.Unusable -> R.string.account_error_unusable
        is AccountError.MergeRejected -> reason.messageRes()
        AccountError.NoBankApp -> R.string.account_error_no_bank
        AccountError.InvalidMask -> R.string.account_edit_mask_error
        is AccountError.IdentifierTaken -> R.string.account_error_identifier_taken
    }

@StringRes
fun MergeRejection.messageRes(): Int =
    when (this) {
        MergeRejection.SAME_ACCOUNT -> R.string.account_merge_rejected_same
        MergeRejection.ACCOUNT_GONE -> R.string.account_merge_rejected_gone
        MergeRejection.DIFFERENT_BANK -> R.string.account_merge_rejected_bank
        MergeRejection.DIFFERENT_TYPE -> R.string.account_merge_rejected_type
        MergeRejection.HAS_TRANSFER -> R.string.accounts_snack_transfer
        MergeRejection.ACCOUNT_ARCHIVED -> R.string.account_merge_rejected_archived
    }
