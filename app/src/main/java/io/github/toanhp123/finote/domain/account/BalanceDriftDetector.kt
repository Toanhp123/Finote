package io.github.toanhp123.finote.domain.account

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.BalanceDrift
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

private val LEDGER_ORDER = compareBy<Transaction>({ it.timestamp }, { it.createdAt }, { it.id })

/**
 * Compares the bank balance on the newest live transaction of [account] that carries one (P) with the app's balance
 * after the live transactions at or before P. Null for a credit card, when no such P exists, when the two match, or
 * when an adjustment of the account is ordered after P (the user already settled it).
 */
fun detectBalanceDrift(
    account: Account,
    transactions: List<Transaction>,
): BalanceDrift? {
    val mine =
        if (account.type == AccountType.CREDIT_CARD) {
            emptyList() // no balance to compare; p is -1 below
        } else {
            transactions.filter { it.deletedAt == null && it.isOf(account.id) }.sortedWith(LEDGER_ORDER)
        }
    // The post balance belongs to the notification's own account, so a transfer received here never counts as P.
    val p = mine.indexOfLast { it.accountId == account.id && it.postBalance != null }
    if (p < 0 || mine.drop(p + 1).any { it.type == TransactionType.ADJUSTMENT }) return null
    val expected = BalanceCalculator.balance(account.id, account.initialBalance, mine.take(p + 1))
    val bank = checkNotNull(mine[p].postBalance)
    return if (bank == expected) null else BalanceDrift(account.id, Math.subtractExact(bank, expected))
}

private fun Transaction.isOf(accountId: String): Boolean =
    this.accountId == accountId || (type == TransactionType.TRANSFER && toAccountId == accountId)
