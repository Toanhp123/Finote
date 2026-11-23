package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.account.model.DriftKey
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * True when the bank balance after the account's balance anchor (see [findBalanceAnchor]) differs from the app's. False
 * with no anchor, or when an adjustment of the account is ordered after the anchor (the user already settled it).
 * Only a verdict for [balanceDriftKey]: the difference is never shown or booked.
 */
internal fun hasAnchorDrift(
    account: Account,
    transactions: List<Transaction>,
): Boolean {
    val anchor = findBalanceAnchor(account, transactions)
    if (anchor == null || anchor.settledAfter.isNotEmpty()) return false
    return anchor.postBalance != BalanceCalculator.balance(account.id, account.initialBalance, anchor.ledger)
}

/**
 * Non-null when the bank balance readings of the account no longer chain from its start, whatever order they arrived
 * in. The key is the bank time of the newest reading and the ledger balance through it. Null for a credit card, with no
 * reading, or when an adjustment is ordered after the newest reading (the user already settled it).
 */
fun balanceDriftKey(
    account: Account,
    transactions: List<Transaction>,
): DriftKey? {
    if (account.type == AccountType.CREDIT_CARD) return null
    val x = account.id
    val mine = transactions.filter { it.touches(x) }.sortedWith(effectiveOrder(x))
    val b = mine.indexOfLast { it.balanceEdge(x) != null }
    val settled = mine.drop(b + 1).any { it.type == TransactionType.ADJUSTMENT && it.accountId == x }
    return if (b < 0 || settled) null else driftKeyAt(account, transactions, mine, b)
}

/** [mine] is the account's live rows in [effectiveOrder]; [b] indexes its newest balance row. */
private fun driftKeyAt(
    account: Account,
    transactions: List<Transaction>,
    mine: List<Transaction>,
    b: Int,
): DriftKey? {
    val x = account.id
    val time = effectiveTime(mine[b], x)
    val key = DriftKey(time, BalanceCalculator.balance(x, account.initialBalance, mine.take(b + 1)))
    val c = mine.checkpointBefore(account, b, time - BALANCE_ORDER_WINDOW_MILLIS)
    val start = c?.let { checkNotNull(mine[it].anchorBalance(x)) } ?: account.initialBalance
    val window = mine.subList((c ?: -1) + 1, b + 1)
    // A row without a bank balance cannot be chained; keep today's order-based verdict.
    val unchainable = { row: Transaction ->
        row.balanceEdge(x) == null || (row.type == TransactionType.ADJUSTMENT && row.accountId == x)
    }
    return if (window.any(unchainable)) {
        key.takeIf { hasAnchorDrift(account, transactions) }
    } else {
        key.takeUnless { chainsFrom(start, window.map { row -> checkNotNull(row.balanceEdge(x)) }) }
    }
}

/** Index of the newest balance row before [limit] rows whose bank balance equals the running ledger; null if none. */
private fun List<Transaction>.checkpointBefore(
    account: Account,
    limit: Int,
    before: Long,
): Int? {
    var running = account.initialBalance
    var found: Int? = null
    for (i in 0 until limit) {
        running = BalanceCalculator.balance(account.id, running, listOf(this[i]))
        if (this[i].anchorBalance(account.id) == running && effectiveTime(this[i], account.id) < before) found = i
    }
    return found
}
