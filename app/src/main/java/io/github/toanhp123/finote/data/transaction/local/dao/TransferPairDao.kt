package io.github.toanhp123.finote.data.transaction.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import io.github.toanhp123.finote.data.transaction.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/** Queries for auto-paired transfers: the candidates a new row may pair with, and the originals a pair replaced. */
@Dao
interface TransferPairDao {
    /**
     * Live notification rows of this [amount] with a timestamp in [from]..[to] that nothing has touched: unpaired,
     * `updatedAt = createdAt`, income or expense. Only narrows; `findTransferPartner` decides.
     */
    @Query(
        "SELECT * FROM transactions WHERE deletedAt IS NULL AND source = 'NOTIFICATION_PARSER' " +
            "AND pairedIntoId IS NULL AND updatedAt = createdAt AND type IN ('EXPENSE', 'INCOME') " +
            "AND amount = :amount AND timestamp BETWEEN :from AND :to",
    )
    suspend fun getPairCandidates(
        amount: Long,
        from: Long,
        to: Long,
    ): List<TransactionEntity>

    /** The soft-deleted originals an auto-pair folded into [transferId], oldest first. */
    @Query("SELECT * FROM transactions WHERE pairedIntoId = :transferId ORDER BY createdAt ASC, id ASC")
    suspend fun getPairedOriginals(transferId: String): List<TransactionEntity>

    /** Ids of the transfers that have folded originals behind them. */
    @Query("SELECT DISTINCT pairedIntoId FROM transactions WHERE pairedIntoId IS NOT NULL")
    fun observeAutoPairedIds(): Flow<List<String>>
}
