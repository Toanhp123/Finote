package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/** Ledger order shared by every balance rule: timestamp, then createdAt, then id. */
internal val LEDGER_ORDER: Comparator<Transaction> = compareBy({ it.timestamp }, { it.createdAt }, { it.id })

/**
 * Newest live row that carries a bank balance for the account: its own row with a post balance, or a paired transfer
 * into it with `toPostBalance` and `toTimestamp`. A candidate only counts when the ledger order agrees with the real
 * order of the touching rows. [ledger] is every touching row up to it; [settledAfter] is the ADJUSTMENT rows of the
 * account ordered after it.
 */
internal data class BalanceAnchor(
    val postBalance: Long,
    val ledger: List<Transaction>,
    val settledAfter: List<Transaction>,
) {
    // Balances must never reach a log or crash report.
    override fun toString() = "BalanceAnchor(ledger=${ledger.size}, settledAfter=${settledAfter.size})"
}

/**
 * Null for a credit card, or when no candidate row passes the order check. A transfer received by the account anchors
 * only through its receiver balance; a plain post balance on it belongs to the sender.
 */
internal fun findBalanceAnchor(
    account: Account,
    transactions: List<Transaction>,
): BalanceAnchor? {
    if (account.type == AccountType.CREDIT_CARD) return null
    val x = account.id
    val mine = transactions.filter { it.touches(x) }.sortedWith(LEDGER_ORDER)
    // shortcut: O(n) per candidate, worst case O(n²) when many candidates fail; fine per account until step 30.
    val p = mine.indices.reversed().firstOrNull { mine[it].anchorBalance(x) != null && mine.agreesAround(mine[it], x) }
    return p?.let {
        BalanceAnchor(
            postBalance = checkNotNull(mine[it].anchorBalance(x)),
            ledger = mine.take(it + 1),
            settledAfter =
                mine.drop(it + 1).filter { row -> row.type == TransactionType.ADJUSTMENT && row.accountId == x },
        )
    }
}

/** Receiver time of a paired transfer into [x]; null for any other row. */
private fun Transaction.receivedAt(x: String): Long? =
    toTimestamp.takeIf { type == TransactionType.TRANSFER && toAccountId == x }

/** Time the bank applied [row] to account [x]: the receiver time of a paired transfer into x, else its timestamp. */
private fun effectiveTime(
    row: Transaction,
    x: String,
): Long = row.receivedAt(x) ?: row.timestamp

private fun Transaction.anchorBalance(x: String): Long? =
    if (accountId == x) {
        postBalance
    } else if (receivedAt(x) != null) {
        toPostBalance
    } else {
        null
    }

/** True when ledger order and real order of every other row of the account agree around [candidate]. */
private fun List<Transaction>.agreesAround(
    candidate: Transaction,
    x: String,
): Boolean {
    val at = effectiveTime(candidate, x)
    return all { row ->
        if (row === candidate) return@all true
        val rowAt = effectiveTime(row, x)
        if (rowAt == at) {
            // Equal plain timestamps keep the ledger tie-break; an equal receiver time cannot be ordered.
            candidate.receivedAt(x) == null && row.receivedAt(x) == null
        } else {
            (LEDGER_ORDER.compare(row, candidate) < 0) == (rowAt < at)
        }
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
