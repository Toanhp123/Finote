package io.github.toanhp123.finote.domain.account.model

/** The digits a bank app shows for one account, card or wallet. The key is (packageName, kind, mask). */
data class AccountIdentifier(
    val packageName: String,
    val kind: IdentifierKind,
    val mask: String,
    val accountId: String,
) {
    // The mask stays out of logs and crash reports.
    override fun toString() = "AccountIdentifier(packageName=$packageName, kind=$kind, accountId=$accountId)"
}
