package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.transaction.model.Transaction

/**
 * Compares the bank balance on the account's balance anchor (see [findBalanceAnchor]) with the app's balance after the
 * anchor's ledger. Null when there is no anchor, when the two match, or when an adjustment of the account is ordered
 * after the anchor (the user already settled it).
 */
fun detectBalanceDrift(
    account: Account,
    transactions: List<Transaction>,
): BalanceDrift? {
    val anchor = findBalanceAnchor(account, transactions)
    if (anchor == null || anchor.settledAfter.isNotEmpty()) return null
    val expected = BalanceCalculator.balance(account.id, account.initialBalance, anchor.ledger)
    return if (anchor.postBalance == expected) {
        null
    } else {
        BalanceDrift(account.id, Math.subtractExact(anchor.postBalance, expected))
    }
}
