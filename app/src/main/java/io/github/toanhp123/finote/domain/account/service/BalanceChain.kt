package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.transaction.model.Transaction

/** Two bank balance readings this far apart or closer may arrive out of order. */
internal const val BALANCE_ORDER_WINDOW_MILLIS: Long = 30 * 60_000L

/** One bank-reported step of an account: the balance before the row and the balance after it. */
internal data class BalanceEdge(
    val from: Long,
    val to: Long,
) {
    // Balances must never reach a log or crash report.
    override fun toString() = "BalanceEdge"
}

/** Null when the row carries no bank balance for [x]. */
internal fun Transaction.balanceEdge(x: String): BalanceEdge? =
    anchorBalance(x)?.let { after ->
        BalanceEdge(from = Math.subtractExact(after, BalanceCalculator.balance(x, 0L, listOf(this))), to = after)
    }

/** Real order of the rows of account [x]: bank time, then createdAt, then id. */
internal fun effectiveOrder(x: String): Comparator<Transaction> =
    compareBy({ effectiveTime(it, x) }, { it.createdAt }, { it.id })

/** True when [edges], in some order, form one walk that starts at [start] and uses every edge once. */
internal fun chainsFrom(
    start: Long,
    edges: List<BalanceEdge>,
): Boolean {
    if (edges.isEmpty()) return true
    val skew = edges.skew()
    val degreesFit =
        if (skew.isEmpty()) edges.any { start == it.from || start == it.to } else skew[start] == 1 && skew.size == 2
    return degreesFit && edges.reachableFrom(start)
}

/** The only start [edges] can chain from, or null when none or more than one start fits. */
internal fun uniqueStart(edges: List<BalanceEdge>): Long? {
    val skew = edges.skew()
    val head = skew.entries.singleOrNull { it.value == 1 }?.key ?: return null
    return head.takeIf { skew.size == 2 && skew.values.count { v -> v == -1 } == 1 && edges.reachableFrom(head) }
}

/** Per vertex, out-degree minus in-degree; balanced vertices are left out. */
private fun List<BalanceEdge>.skew(): Map<Long, Int> {
    val out = HashMap<Long, Int>()
    forEach {
        out.merge(it.from, 1, Int::plus)
        out.merge(it.to, -1, Int::plus)
    }
    return out.filterValues { it != 0 }
}

/** True when a walk along edge direction from [start] touches every edge. */
private fun List<BalanceEdge>.reachableFrom(start: Long): Boolean {
    val byFrom = HashMap<Long, MutableList<Int>>()
    forEachIndexed { i, e -> byFrom.getOrPut(e.from) { mutableListOf() }.add(i) }
    val seen = HashSet<Long>()
    val queue = ArrayDeque(listOf(start))
    var reached = 0
    while (queue.isNotEmpty()) {
        val v = queue.removeFirst()
        if (!seen.add(v)) continue
        byFrom[v]?.forEach { i ->
            reached++
            queue.addLast(this[i].to)
        }
    }
    return reached == size
}
