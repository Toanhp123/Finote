package io.github.toanhp123.finote.domain.notification

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Ignored
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Rejected
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Resolved
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Book
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Fail
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Ignore
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Repost
import kotlin.math.abs

// Failure reasons a settlement run writes to a log.
const val FAIL_AMBIGUOUS_ACCOUNT = "ambiguous_account"
const val FAIL_ACCOUNT_ARCHIVED = "account_archived"
const val FAIL_INVALID_AMOUNT = "invalid_amount"
const val FAIL_SETTLE_ERROR = "settle_error"

// 10^15 VND: far above any real balance, far below Long.MAX_VALUE, so balance sums cannot overflow.
const val MAX_AMOUNT = 1_000_000_000_000_000L

const val PAIR_WINDOW_MILLIS = 30_000L
const val REPOST_WINDOW_WITH_BALANCE_MILLIS = 600_000L

// Without a balance, a short window: a bill split into equal parts posts equal texts seconds apart (spec, Known risks).
const val REPOST_WINDOW_NO_BALANCE_MILLIS = 3_000L

private val LOG_ORDER = compareBy<NotificationLog>({ it.postTime }, { it.id })

/**
 * Decides what becomes of the [pending] logs. A log is due once it is [PAIR_WINDOW_MILLIS] old; until then it waits
 * (but can still be the partner of a due log). Result order: every Book and Fail by the time of the log that
 * carries it, then every Repost in log order. A Repost whose original still waits is not emitted yet.
 * Logs are ordered by postTime, then id; "earlier" always means earlier in that order.
 */
fun planSettlement(
    pending: List<NotificationLog>,
    settled: List<NotificationLog>,
    accounts: List<Account>,
    rulesById: Map<String, ParserRule>,
    now: Long,
): List<SettlementDecision> {
    val ordered = pending.sortedWith(LOG_ORDER)
    val originals = findReposts(ordered, settled)
    val planned = Planner(accounts, rulesById, now).settle(ordered.filter { it.id !in originals })
    val known = settled.map { it.id }.toSet() + planned.flatMap { it.logIds() }
    val reposts =
        ordered.mapNotNull { log ->
            originals[log.id]?.takeIf { it.id in known }?.let { Repost(log, it) }
        }
    return planned + reposts
}

private fun SettlementDecision.logIds(): List<String> =
    when (this) {
        is Book -> listOfNotNull(bankSide.id, walletSide?.id)
        is Fail -> listOfNotNull(log.id, partner?.id)
        is Ignore -> listOfNotNull(log.id, partner?.id)
        is Repost -> emptyList()
    }

/** Maps the id of each repost to the log it repeats. A repost of a repost points at the first one's original. */
private fun findReposts(
    ordered: List<NotificationLog>,
    settled: List<NotificationLog>,
): Map<String, NotificationLog> {
    val originals = HashMap<String, NotificationLog>()
    ordered.forEachIndexed { index, log ->
        val window =
            if (log.parsed?.postBalance != null) {
                REPOST_WINDOW_WITH_BALANCE_MILLIS
            } else {
                REPOST_WINDOW_NO_BALANCE_MILLIS
            }
        val earlier = settled.filter { LOG_ORDER.compare(it, log) < 0 } + ordered.take(index)
        val match =
            earlier
                .filter { it.sameText(log) && log.postTime - it.postTime <= window }
                .maxWithOrNull(LOG_ORDER)
        if (match != null) originals[log.id] = originals[match.id] ?: match
    }
    return originals
}

private fun NotificationLog.sameText(other: NotificationLog) =
    packageName == other.packageName && title == other.title && content == other.content

private class Planner(
    private val accounts: List<Account>,
    private val rulesById: Map<String, ParserRule>,
    now: Long,
) {
    private val dueTime = now - PAIR_WINDOW_MILLIS

    /** Book and Fail decisions for the logs that are not reposts, ordered by the log that carries each. */
    fun settle(pool: List<NotificationLog>): List<SettlementDecision> {
        val valid = pool.filter { it.hasValidAmount() }
        val pairs = pair(valid)
        val paired = pairs.flatMap { listOf(it.first, it.second) }.toSet()
        val invalid =
            pool
                .filter { it.isDue() && !it.hasValidAmount() }
                .map { it to Fail(it, null, FAIL_INVALID_AMOUNT) }
        val pairDecisions = pairs.map { (bank, wallet) -> bank to decide(bank, wallet) }
        val singles = valid.filter { it !in paired && it.isDue() }.map { it to decide(it, null) }
        return (invalid + pairDecisions + singles).sortedWith(compareBy(LOG_ORDER) { it.first }).map { it.second }
    }

    private fun NotificationLog.isDue() = postTime <= dueTime

    private fun NotificationLog.hasValidAmount() = (parsed?.amount ?: 0L) in 1L..MAX_AMOUNT

    private fun NotificationLog.isWallet() = rulesById[matchedRuleId]?.accountType == AccountType.WALLET

    private fun canPair(
        a: NotificationLog,
        b: NotificationLog,
    ): Boolean {
        // Only valid logs (parsed != null) get here.
        // shortcut: a wallet and a bank log of two different payments with the same amount within 30 s merge; accepted.
        return a.packageName != b.packageName &&
            a.isWallet() != b.isWallet() &&
            a.parsed?.amount == b.parsed?.amount &&
            a.parsed?.type == b.parsed?.type &&
            abs(a.postTime - b.postTime) <= PAIR_WINDOW_MILLIS &&
            (a.isDue() || b.isDue())
    }

    /**
     * Greedy: the closest pair first, then the older one. Returns (bankSide, walletSide).
     * shortcut: a later, nearer partner is not considered once one side is due; accepted.
     */
    private fun pair(valid: List<NotificationLog>): List<Pair<NotificationLog, NotificationLog>> {
        val candidates = valid.flatMapIndexed { i, a -> valid.drop(i + 1).filter { canPair(a, it) }.map { a to it } }
        val taken = HashSet<String>()
        val chosen = ArrayList<Pair<NotificationLog, NotificationLog>>()
        val sorted =
            candidates.sortedWith(
                compareBy<Pair<NotificationLog, NotificationLog>>(
                    { it.second.postTime - it.first.postTime },
                    { it.first.postTime },
                    { it.first.id },
                    { it.second.id },
                ),
            )
        for ((a, b) in sorted) {
            if (a.id in taken || b.id in taken) continue
            taken += a.id
            taken += b.id
            chosen += if (a.isWallet()) b to a else a to b
        }
        return chosen
    }

    private fun decide(
        bank: NotificationLog,
        wallet: NotificationLog?,
    ): SettlementDecision {
        val rule = rulesById[bank.matchedRuleId]
        return when (val resolution = resolveAccount(bank.packageName, bank.parsed?.accountMask, rule, accounts)) {
            is Rejected -> {
                Fail(bank, wallet, resolution.reason)
            }

            Ignored -> {
                Ignore(bank, wallet)
            }

            is Resolved -> {
                Book(
                    bankSide = bank,
                    walletSide = wallet,
                    account = resolution.target,
                    merchant = wallet?.parsed?.merchant ?: bank.parsed?.merchant,
                    note = wallet?.parsed?.note ?: bank.parsed?.note,
                )
            }
        }
    }
}
