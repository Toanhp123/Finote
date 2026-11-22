package io.github.toanhp123.finote.domain.notification.service

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.AccountType
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Ignored
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Rejected
import io.github.toanhp123.finote.domain.notification.model.AccountResolution.Resolved
import io.github.toanhp123.finote.domain.notification.model.NotificationLog
import io.github.toanhp123.finote.domain.notification.model.NotificationStatus
import io.github.toanhp123.finote.domain.notification.model.ParserRule
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Book
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Fail
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Ignore
import io.github.toanhp123.finote.domain.notification.model.SettlementDecision.Repost
import io.github.toanhp123.finote.domain.transaction.service.TransactionRules
import java.time.Instant
import kotlin.math.abs

// Failure reasons a settlement run writes to a log.
const val FAIL_AMBIGUOUS_ACCOUNT = "ambiguous_account"
const val FAIL_ACCOUNT_ARCHIVED = "account_archived"
const val FAIL_INVALID_AMOUNT = "invalid_amount"
const val FAIL_INVALID_TIME = "invalid_time"
const val FAIL_SETTLE_ERROR = "settle_error"
const val FAIL_ADJUSTED_AFTER = "adjusted_after"

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
 * carries it, then every Repost in log order. A log with an impossible post time fails at once, due or not.
 * A Repost whose original still waits is not emitted yet.
 * Logs are ordered by postTime, then id; "earlier" means earlier in that order. A Repost is normally of an earlier log;
 * the key rule may also point at a later settled log (an update that was booked before its first version was reparsed).
 */
fun planSettlement(
    pending: List<NotificationLog>,
    settled: List<NotificationLog>,
    accounts: List<Account>,
    identifiers: List<AccountIdentifier>,
    rulesById: Map<String, ParserRule>,
    now: Long,
): List<SettlementDecision> {
    val ordered = pending.sortedWith(LOG_ORDER)
    val originals = findReposts(ordered, settled)
    val planned = Planner(accounts, identifiers, rulesById, now).settle(ordered.filter { it.id !in originals })
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

/**
 * Maps the id of each repost to the log it repeats. A repost of a repost points at the first one's original.
 * The text rule runs first (an earlier log with the same text); the key rule only sees the logs it left
 * (see [KeyClusters]).
 */
private fun findReposts(
    ordered: List<NotificationLog>,
    settled: List<NotificationLog>,
): Map<String, NotificationLog> {
    val originals = HashMap<String, NotificationLog>()
    val clusters = KeyClusters(settled)
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
        if (match != null) {
            originals[log.id] = originals[match.id] ?: match
        } else {
            clusters.place(log)?.let { originals[log.id] = it }
        }
    }
    return originals
}

private fun NotificationLog.sameText(other: NotificationLog) =
    packageName == other.packageName && title == other.title && content == other.content

/**
 * Key rule: a platform updates one notification in place (same key, new text). A cluster is one root plus the logs
 * matched to it. A log joins the nearest root (then the earlier root) of the same package and key with equal
 * amount, type and mask, within [REPOST_WINDOW_WITH_BALANCE_MILLIS] of the root, never of a member, and whose
 * non-null balances in the cluster all equal its own. A settled root may be later than the log (a reparsed first
 * version); a pending root is always earlier in plan order. Only parsed logs take part, so a failed first version
 * is never a root.
 * shortcut: one fixed key per app, equal amount, type and mask, at most one of them with a balance, within 10 min
 * merges two payments; upgrade: per-rule opt-out of the key rule.
 */
private class KeyClusters(
    settled: List<NotificationLog>,
) {
    // A deduplicated log is a root only when no non-deduplicated log shares its key and link (a booked pair's wallet).
    private val roots =
        settled
            .filter { it.isKeyed() && (it.status != NotificationStatus.DEDUPLICATED || it.isPairedWalletSide(settled)) }
            .toMutableList()
    private val balances = HashMap<String, MutableSet<Long>>()

    init {
        for (root in roots) {
            // Balances of a root's deduplicated siblings count: they were part of the same booked payment.
            val siblings =
                settled.filter {
                    it.status == NotificationStatus.DEDUPLICATED && it.notificationKey == root.notificationKey &&
                        it.linkedTransactionId != null && it.linkedTransactionId == root.linkedTransactionId
                }
            balances[root.id] = (listOf(root) + siblings).mapNotNullTo(HashSet()) { it.parsed?.postBalance }
        }
    }

    /** Returns the root [log] joins, or null: then a keyed, parsed [log] becomes a root itself. */
    fun place(log: NotificationLog): NotificationLog? {
        if (!log.isKeyed()) return null
        val root =
            roots
                .filter { it.accepts(log) }
                .minWithOrNull(compareBy<NotificationLog> { abs(it.postTime - log.postTime) }.then(LOG_ORDER))
        val balance = log.parsed?.postBalance
        if (root == null) {
            roots += log
            balances[log.id] = listOfNotNull(balance).toMutableSet()
        } else if (balance != null) {
            balances.getValue(root.id) += balance
        }
        return root
    }

    private fun NotificationLog.isKeyed() = notificationKey != null && parsed != null

    /** The first log of its key and link, none of them non-deduplicated: its later reposts never root, so no creep. */
    private fun NotificationLog.isPairedWalletSide(settled: List<NotificationLog>) =
        linkedTransactionId != null &&
            settled.none {
                it.notificationKey == notificationKey &&
                    it.linkedTransactionId == linkedTransactionId &&
                    (it.status != NotificationStatus.DEDUPLICATED || LOG_ORDER.compare(it, this) < 0)
            }

    private fun NotificationLog.accepts(log: NotificationLog): Boolean {
        val a = parsed
        val b = log.parsed
        return a != null && b != null &&
            packageName == log.packageName && notificationKey == log.notificationKey &&
            a.amount == b.amount && a.type == b.type && a.accountMask == b.accountMask &&
            abs(log.postTime - postTime) <= REPOST_WINDOW_WITH_BALANCE_MILLIS &&
            (b.postBalance == null || balances.getValue(id).all { it == b.postBalance })
    }
}

private class Planner(
    private val accounts: List<Account>,
    private val identifiers: List<AccountIdentifier>,
    private val rulesById: Map<String, ParserRule>,
    private val now: Long,
) {
    private val dueTime = now - PAIR_WINDOW_MILLIS

    /** Book and Fail decisions for the logs that are not reposts, ordered by the log that carries each. */
    fun settle(allLogs: List<NotificationLog>): List<SettlementDecision> {
        // An impossible post time can never be booked and never becomes due: fail it now, before pairing.
        val (pool, badTime) = allLogs.partition { it.hasValidTime() }
        val valid = pool.filter { it.hasValidAmount() }
        val pairs = pair(valid)
        val paired = pairs.flatMap { listOf(it.first, it.second) }.toSet()
        val invalid =
            pool
                .filter { it.isDue() && !it.hasValidAmount() }
                .map { it to Fail(it, null, FAIL_INVALID_AMOUNT) }
        val timeFailures = badTime.map { it to Fail(it, null, FAIL_INVALID_TIME) }
        val pairDecisions = pairs.map { (bank, wallet) -> bank to decide(bank, wallet) }
        val singles = valid.filter { it !in paired && it.isDue() }.map { it to decide(it, null) }
        val decisions = timeFailures + invalid + pairDecisions + singles
        return decisions.sortedWith(compareBy(LOG_ORDER) { it.first }).map { it.second }
    }

    private fun NotificationLog.isDue() = postTime <= dueTime

    private fun NotificationLog.hasValidTime() = TransactionRules.isValidTimestamp(postTime, Instant.ofEpochMilli(now))

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
        return when (
            val resolution =
                resolveAccount(
                    bank.packageName,
                    bank.parsed?.accountMask,
                    rule,
                    accounts,
                    identifiers,
                )
        ) {
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
                    rule = rule,
                )
            }
        }
    }
}
