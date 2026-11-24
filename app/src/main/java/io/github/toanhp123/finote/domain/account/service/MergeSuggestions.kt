package io.github.toanhp123.finote.domain.account.service

import io.github.toanhp123.finote.domain.account.model.Account

/** The pair as `(min, max)` by id: the one form a dismissal is stored and looked up in. */
fun orderedPair(
    a: String,
    b: String,
): Pair<String, String> = if (a <= b) a to b else b to a

/**
 * Pairs of live accounts that look like one real bank account: `AccountRules.checkMerge` allows them (transfers are
 * not counted here), at least one has no identifier, and the pair is not dismissed. [identified] may hold ids of
 * deleted owners; only ids of [accounts] are looked up.
 */
fun mergeCandidatePairs(
    accounts: List<Account>,
    identified: Set<String>,
    dismissed: Set<Pair<String, String>>,
): List<Pair<Account, Account>> {
    val live = accounts.filter { it.deletedAt == null && !it.isArchived }
    return live
        .flatMapIndexed { index, a -> live.drop(index + 1).map { b -> a to b } }
        .filter { (a, b) ->
            (a.id !in identified || b.id !in identified) &&
                orderedPair(a.id, b.id) !in dismissed &&
                AccountRules.checkMerge(a.id, b.id, a, b, transfersBetween = 0) == null
        }
}

/**
 * Account id to the id of the account it should merge with. [hasTransferBetween] gets the pair as `(min, max)` and is
 * asked for candidate pairs only. Per account the partner with identifiers wins, then the oldest `createdAt`, then
 * the lowest id.
 */
fun mergeSuggestions(
    accounts: List<Account>,
    identified: Set<String>,
    dismissed: Set<Pair<String, String>>,
    hasTransferBetween: (String, String) -> Boolean,
): Map<String, String> {
    val partnerOrder =
        compareByDescending<Account> { it.id in identified }.thenBy { it.createdAt }.thenBy { it.id }
    return mergeCandidatePairs(accounts, identified, dismissed)
        .filterNot { (a, b) -> orderedPair(a.id, b.id).let { (lo, hi) -> hasTransferBetween(lo, hi) } }
        .flatMap { (a, b) -> listOf(a to b, b to a) }
        .groupBy({ it.first.id }, { it.second })
        .mapValues { (_, partners) -> partners.minWith(partnerOrder).id }
}
