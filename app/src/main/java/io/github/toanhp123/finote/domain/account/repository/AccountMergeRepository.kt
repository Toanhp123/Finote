package io.github.toanhp123.finote.domain.account.repository

/**
 * Writes and reads that only serve folding one account into another. A write is one atomic unit; business rules
 * live in `AccountRules`, and the use case runs them before it writes.
 */
interface AccountMergeRepository {
    /**
     * Folds [sourceId] into [targetId], two rows of one bank account. One write transaction. The caller has already
     * checked `AccountRules.checkMerge`.
     */
    suspend fun merge(
        sourceId: String,
        targetId: String,
        now: Long,
    )

    /**
     * The source's `initialBalance` when merging it into [targetId] would drop that start: both accounts are
     * initialized, no merged row carries a bank balance to re-anchor the target on, and that start is not zero. Null
     * otherwise. Reads only.
     */
    suspend fun getMergeDroppedStart(
        sourceId: String,
        targetId: String,
    ): Long?

    /** Transfers between the two accounts in either direction, deleted ones included. */
    suspend fun countTransfersBetween(
        sourceId: String,
        targetId: String,
    ): Int
}
