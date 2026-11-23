package io.github.toanhp123.finote.domain.account.usecase

import io.github.toanhp123.finote.domain.account.repository.AccountMergeRepository

/** Does not copy: the merge itself. It only records each call; business rules live in `AccountRules`. */
class FakeAccountMergeRepository : AccountMergeRepository {
    /** Each merge call as (sourceId, targetId, now). The fake moves nothing. */
    val merges = mutableListOf<Triple<String, String, Long>>()

    override suspend fun merge(
        sourceId: String,
        targetId: String,
        now: Long,
    ) {
        merges += Triple(sourceId, targetId, now)
    }

    /** What [getMergeDroppedStart] answers per (sourceId, targetId); null when absent. */
    val droppedStarts = mutableMapOf<Pair<String, String>, Long>()

    override suspend fun getMergeDroppedStart(
        sourceId: String,
        targetId: String,
    ): Long? = droppedStarts[sourceId to targetId]

    /** What [countTransfersBetween] answers per unordered pair of ids; 0 when absent. */
    val transferCounts = mutableMapOf<Set<String>, Int>()

    override suspend fun countTransfersBetween(
        sourceId: String,
        targetId: String,
    ): Int = transferCounts[setOf(sourceId, targetId)] ?: 0
}
