package io.github.toanhp123.finote.ui.settings.accountedit.logic

import androidx.annotation.StringRes
import io.github.toanhp123.finote.R
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind

@StringRes
fun IdentifierKind.labelRes(): Int =
    when (this) {
        IdentifierKind.ACCOUNT -> R.string.account_edit_kind_account
        IdentifierKind.CARD -> R.string.account_edit_kind_card
        IdentifierKind.WALLET -> R.string.account_edit_kind_wallet
    }

/** Only the four types the create screen offers; a credit card is never created here. */
@StringRes
fun AccountType.labelRes(): Int =
    when (this) {
        AccountType.BANK, AccountType.CREDIT_CARD -> R.string.account_type_bank
        AccountType.WALLET -> R.string.account_type_wallet
        AccountType.CASH -> R.string.account_type_cash
        AccountType.SAVINGS -> R.string.account_type_savings
    }
