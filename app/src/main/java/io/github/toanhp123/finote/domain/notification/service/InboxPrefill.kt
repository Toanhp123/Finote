package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.notification.model.FailedInboxItem
import io.github.toanhp123.finote.domain.notification.model.InboxPrefill
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

private const val MASK_TAIL = 4

/**
 * Prefill of the cash entry for [item]. The account is a plain identifier lookup, never `resolveAccount`: no lone
 * account fallback, no attach, no new account. Empty beats wrong, a wrong account books real money.
 */
fun prefillFor(
    item: FailedInboxItem,
    identifiers: List<AccountIdentifier>,
    accounts: List<Account>,
): InboxPrefill {
    val parsed = item.parsed
    val type = parsed?.type ?: extractSignedType(item.title, item.content)
    val mask =
        if (item.reason == FAIL_NO_RULE_MATCHED) extractMaskLast4(item.title, item.content) else parsed?.accountMask
    val owner = mask?.let { singleOwner(item.packageName, it, identifiers, accounts) }
    return InboxPrefill(
        type = type ?: TransactionType.EXPENSE,
        amount = parsed?.amount?.takeIf { it > 0 } ?: extractVndAmount(item.title, item.content),
        accountId = owner?.takeIf { it.isUsable }?.id,
        // Only an archived owner that is not ignored is named.
        archivedAccountName = owner?.takeIf { it.isArchived && !it.isIgnored }?.name,
        note = (parsed?.merchant ?: parsed?.note ?: extractMerchant(item.title, item.content)).orEmpty(),
        // The booking keeps this exact time; invalid_time has none to keep.
        timestamp = item.postTime.takeIf { item.reason != FAIL_INVALID_TIME },
    )
}

/** The one live owner of [mask] in [packageName], any kind, on the last 4 digits of both; null for none or several. */
private fun singleOwner(
    packageName: String,
    mask: String,
    identifiers: List<AccountIdentifier>,
    accounts: List<Account>,
): Account? =
    identifiers
        .filter { it.packageName == packageName && it.mask.takeLast(MASK_TAIL) == mask.takeLast(MASK_TAIL) }
        .mapNotNull { id -> accounts.firstOrNull { it.id == id.accountId && it.deletedAt == null } }
        .distinctBy { it.id }
        .singleOrNull()
