package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/** Ledger order shared by every balance rule: timestamp, then createdAt, then id. */
internal val LEDGER_ORDER: Comparator<Transaction> = compareBy({ it.timestamp }, { it.createdAt }, { it.id })

/**
 * Newest live row whose own account is the account and that carries a bank balance. [ledger] is every touching row up
 * to it; [settledAfter] is the ADJUSTMENT rows of the account ordered after it.
 */
internal data class BalanceAnchor(
    val postBalance: Long,
    val ledger: List<Transaction>,
    val settledAfter: List<Transaction>,
)

/**
 * Null for a credit card, or when no live row with `accountId == account.id` carries a post balance. A transfer
 * received by the account never anchors: its post balance belongs to the sender.
 */
internal fun findBalanceAnchor(
    account: Account,
    transactions: List<Transaction>,
): BalanceAnchor? {
    if (account.type == AccountType.CREDIT_CARD) return null
    val mine = transactions.filter { it.touches(account.id) }.sortedWith(LEDGER_ORDER)
    val p = mine.indexOfLast { it.accountId == account.id && it.postBalance != null }
    return mine.getOrNull(p)?.let { anchor ->
        BalanceAnchor(
            postBalance = checkNotNull(anchor.postBalance),
            ledger = mine.take(p + 1),
            settledAfter =
                mine.drop(p + 1).filter { it.type == TransactionType.ADJUSTMENT && it.accountId == account.id },
        )
    }
}

/**
 * Initial balance that makes `ledger + settledAfter` end at the anchor's post balance; null when there is no anchor.
 */
fun BalanceCalculator.anchoredInitialBalance(
    account: Account,
    transactions: List<Transaction>,
): Long? =
    findBalanceAnchor(account, transactions)?.let {
        Math.subtractExact(it.postBalance, balance(account.id, 0L, it.ledger + it.settledAfter))
    }
