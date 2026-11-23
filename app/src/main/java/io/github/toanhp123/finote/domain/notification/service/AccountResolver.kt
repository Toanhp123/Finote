package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
import io.github.toanhp123.finote.domain.account.model.defaultKind
import io.github.toanhp123.finote.domain.notification.model.AccountResolution
import io.github.toanhp123.finote.domain.notification.model.AccountTarget
import io.github.toanhp123.finote.domain.notification.model.ParserRule

/**
 * Picks the account of one parsed notification from its app [packageName] and the [mask] it shows.
 * Only live accounts of the package count (archived and ignored ones too); [accounts] may hold deleted rows.
 * With a mask, the key (package, kind, mask) is looked up in [identifiers]; an identifier whose owner is not live is
 * free. A missing key attaches to the only account of the rule's type when it holds no identifier yet, otherwise the
 * notification gets a new account. Without a mask, the package must hold exactly one live account of the rule's type.
 * An ignored match gives [AccountResolution.Ignored], an archived one is rejected.
 */
internal fun resolveAccount(
    packageName: String,
    mask: String?,
    rule: ParserRule?,
    accounts: List<Account>,
    identifiers: List<AccountIdentifier>,
): AccountResolution {
    val live = accounts.filter { it.deletedAt == null && it.bankPackageName == packageName }
    val kind = rule.identifierKind()
    val type = rule.accountTypeOrBank()
    val ofType = live.filter { it.type == type }
    if (mask == null && ofType.size > 1) return AccountResolution.Rejected(FAIL_AMBIGUOUS_ACCOUNT)
    val only = ofType.singleOrNull()
    val owner =
        identifiers
            .firstOrNull { it.packageName == packageName && it.kind == kind && it.mask == mask }
            ?.let { id -> live.firstOrNull { it.id == id.accountId } }
    val (found, attach) =
        when {
            mask == null -> {
                only to null
            }

            owner != null -> {
                owner to null
            }

            only != null && identifiers.none { it.accountId == only.id } -> {
                only to AccountIdentifier(packageName, kind, mask, only.id)
            }

            else -> {
                null to null
            }
        }
    return found?.resolution(attach)
        ?: AccountResolution.Resolved(
            AccountTarget.New(packageName, mask, rule?.bankName ?: packageName, type, kind),
        )
}

internal fun ParserRule?.identifierKind(): IdentifierKind = this?.maskKind ?: this?.accountType.defaultKind()

private fun ParserRule?.accountTypeOrBank() = this?.accountType ?: AccountType.BANK

private fun Account.resolution(attach: AccountIdentifier?) =
    when {
        isIgnored -> AccountResolution.Ignored
        isArchived -> AccountResolution.Rejected(FAIL_ACCOUNT_ARCHIVED)
        else -> AccountResolution.Resolved(AccountTarget.Existing(id, attach))
    }
