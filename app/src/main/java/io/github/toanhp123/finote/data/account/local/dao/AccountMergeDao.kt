package io.github.toanhp123.finote.data.account.local.dao

import androidx.room3.Dao
import androidx.room3.Query

/** Queries that only serve the account merge. They span the accounts and transactions tables. */
@Dao
interface AccountMergeDao {
    /** Transfers between the two accounts in either direction, deleted ones included. */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE (accountId = :a AND toAccountId = :b) OR (accountId = :b AND toAccountId = :a)",
    )
    suspend fun countTransfersBetween(
        a: String,
        b: String,
    ): Int

    /** Every transaction with [from] as source, deleted ones too, so a restore lands on [to]. */
    @Query("UPDATE transactions SET accountId = :to, updatedAt = :now WHERE accountId = :from")
    suspend fun moveSource(
        from: String,
        to: String,
        now: Long,
    ): Int

    /** Every transaction with [from] as transfer destination, deleted ones too. */
    @Query("UPDATE transactions SET toAccountId = :to, updatedAt = :now WHERE toAccountId = :from")
    suspend fun moveDestination(
        from: String,
        to: String,
        now: Long,
    ): Int

    /** Points every row merged into [from] at [to], so a chain of merges stays one hop. */
    @Query("UPDATE accounts SET mergedIntoId = :to WHERE mergedIntoId = :from")
    suspend fun repointMerged(
        from: String,
        to: String,
    )
}
