package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountResolution
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.ParserRule

/**
 * Picks the account of one parsed notification from its app [packageName] and the account [mask].
 * Deleted accounts and other apps' accounts are ignored. With a mask, accounts match on the mask; without one,
 * the package must hold exactly one account (archived ones count). An archived match is rejected.
 */
internal fun resolveAccount(
    packageName: String,
    mask: String?,
    rule: ParserRule?,
    accounts: List<Account>,
): AccountResolution {
    val ofPackage = accounts.filter { it.deletedAt == null && it.bankPackageName == packageName }
    val matches = if (mask == null) ofPackage else ofPackage.filter { it.accountNumberMask == mask }
    return when {
        matches.isEmpty() -> {
            AccountResolution.Resolved(newTarget(packageName, mask, rule))
        }

        mask == null && matches.size > 1 -> {
            AccountResolution.Rejected(FAIL_AMBIGUOUS_ACCOUNT)
        }

        // Two active accounts on one key: the first wins (the merge branch removes such duplicates).
        // A card mask is not an account mask, so a card notification can still land on the new account.
        else -> {
            matches
                .firstOrNull { !it.isArchived }
                ?.let { AccountResolution.Resolved(AccountTarget.Existing(it.id)) }
                ?: AccountResolution.Rejected(FAIL_ACCOUNT_ARCHIVED)
        }
    }
}

private fun newTarget(
    packageName: String,
    mask: String?,
    rule: ParserRule?,
) = AccountTarget.New(packageName, mask, rule?.bankName ?: packageName, rule?.accountType ?: AccountType.BANK)
