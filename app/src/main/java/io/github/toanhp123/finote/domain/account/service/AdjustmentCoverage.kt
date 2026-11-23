package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * True when a late log at [time] on [accountId] is already covered by an adjustment the user made: a live ADJUSTMENT of
 * the account after [time], with a live row carrying a bank balance for the account in `(time, adjustment.timestamp]`.
 * The balance row is required because an adjustment within ~30 s of a real log can be a race with that log, not a
 * correction that includes it. [transactions] may hold any account and deleted rows; they are filtered here.
 */
fun isCoveredByAdjustment(
    accountId: String,
    time: Long,
    transactions: List<Transaction>,
): Boolean {
    val mine = transactions.filter { it.touches(accountId) }
    val balanceTimes = mine.filter { it.anchorBalance(accountId) != null }.map { effectiveTime(it, accountId) }
    return mine
        .filter { it.type == TransactionType.ADJUSTMENT && it.accountId == accountId && it.timestamp > time }
        .any { adj -> balanceTimes.any { it > time && it <= adj.timestamp } }
}
