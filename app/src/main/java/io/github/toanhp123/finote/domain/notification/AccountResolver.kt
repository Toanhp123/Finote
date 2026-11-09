package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountResolution
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.ParserRule

/**
 * Picks the account of one parsed notification from its app [packageName] and the account [mask].
 * With a mask, the key (package, mask) is looked up among live accounts first, then among accounts merged away
 * (deleted with a `mergedIntoId`; the latest merge wins) and followed to their live target. A missing or deleted
 * target is no match. Without a mask, the package must hold exactly one live account (archived and ignored ones
 * count; merged rows never do). An ignored match gives [AccountResolution.Ignored], an archived one is rejected.
 */
internal fun resolveAccount(
    packageName: String,
    mask: String?,
    rule: ParserRule?,
    accounts: List<Account>,
): AccountResolution {
    val ofPackage = accounts.filter { it.bankPackageName == packageName }
    val live = ofPackage.filter { it.deletedAt == null }
    if (mask == null && live.size > 1) return AccountResolution.Rejected(FAIL_AMBIGUOUS_ACCOUNT)
    val found = if (mask == null) live.singleOrNull() else findByMask(ofPackage, live, mask)
    return when {
        found == null -> AccountResolution.Resolved(newTarget(packageName, mask, rule))
        found.isIgnored -> AccountResolution.Ignored
        found.isArchived -> AccountResolution.Rejected(FAIL_ACCOUNT_ARCHIVED)
        else -> AccountResolution.Resolved(AccountTarget.Existing(found.id))
    }
}

private fun findByMask(
    ofPackage: List<Account>,
    live: List<Account>,
    mask: String,
): Account? {
    val liveMatches = live.filter { it.accountNumberMask == mask }
    // Two live accounts on one key: the first non-archived wins (merging removes such duplicates).
    // A card mask is not an account mask, so a card notification can still land on the new account.
    if (liveMatches.isNotEmpty()) return liveMatches.firstOrNull { !it.isArchived } ?: liveMatches.first()
    val targetId =
        ofPackage
            .filter { it.deletedAt != null && it.mergedIntoId != null && it.accountNumberMask == mask }
            .maxByOrNull { it.deletedAt ?: 0L }
            ?.mergedIntoId
    return live.firstOrNull { it.id == targetId }
}

private fun newTarget(
    packageName: String,
    mask: String?,
    rule: ParserRule?,
) = AccountTarget.New(packageName, mask, rule?.bankName ?: packageName, rule?.accountType ?: AccountType.BANK)
