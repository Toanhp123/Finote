package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * Start of a young (provisional) account, derived from the bank balances of its rows whatever order they arrived in.
 * Null for a credit card, or when no row carries a bank balance.
 *
 * When every row chains (no balance-less row, no ADJUSTMENT) and the edges have one possible start, that start. Else
 * the bank balance before the first balance row, minus the effect of the rows before it: a gap is never folded in.
 */
fun provisionalStart(
    account: Account,
    transactions: List<Transaction>,
): Long? {
    val x = account.id
    val isCard = account.type == AccountType.CREDIT_CARD
    // A card gets an empty list, so it returns null below (keeps the function at two returns for detekt ReturnCount).
    val mine = if (isCard) emptyList() else transactions.filter { it.touches(x) }.sortedWith(effectiveOrder(x))
    val first = mine.indexOfFirst { it.balanceEdge(x) != null }
    if (first < 0) return null
    val edges = mine.map { it.balanceEdge(x) }
    val chainable = mine.none { it.type == TransactionType.ADJUSTMENT && it.accountId == x }
    val head = if (chainable && edges.none { it == null }) uniqueStart(edges.filterNotNull()) else null
    return head ?: Math.subtractExact(
        checkNotNull(edges[first]).from,
        BalanceCalculator.balance(x, 0L, mine.take(first)),
    )
}

/**
 * True when the bank-balance rows of [accountId] span [BALANCE_ORDER_WINDOW_MILLIS] or more, so a late older row can no
 * longer belong to the start. Rows without a bank balance do not count.
 */
fun isStartLocked(
    accountId: String,
    transactions: List<Transaction>,
): Boolean {
    val times =
        transactions
            .filter { it.touches(accountId) && it.balanceEdge(accountId) != null }
            .map { effectiveTime(it, accountId) }
    return times.isNotEmpty() && times.max() - times.min() >= BALANCE_ORDER_WINDOW_MILLIS
}
