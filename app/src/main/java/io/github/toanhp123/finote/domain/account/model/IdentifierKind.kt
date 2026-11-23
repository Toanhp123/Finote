package io.github.toanhp123.finote.domain.account.model

/** What a masked number in a notification names. It is part of the identifier key. */
enum class IdentifierKind {
    ACCOUNT,
    CARD,
    WALLET,
}

/** [IdentifierKind.WALLET] for a wallet, [IdentifierKind.CARD] for a credit card, else an account number. */
internal fun AccountType?.defaultKind(): IdentifierKind =
    when (this) {
        AccountType.WALLET -> IdentifierKind.WALLET
        AccountType.CREDIT_CARD -> IdentifierKind.CARD
        else -> IdentifierKind.ACCOUNT
    }
