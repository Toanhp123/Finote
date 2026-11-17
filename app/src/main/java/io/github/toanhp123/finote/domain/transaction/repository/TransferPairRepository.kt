package io.github.toanhp123.finote.domain.transaction.repository

import io.github.toanhp123.finote.domain.transaction.model.Transaction
import kotlinx.coroutines.flow.Flow

/** Auto-paired transfers: the two original rows folded into one TRANSFER, and the way back. */
interface TransferPairRepository {
    /** The soft-deleted originals an auto-pair folded into [transferId]; empty for any other transaction. */
    suspend fun getPairedOriginals(transferId: String): List<Transaction>

    /** Ids of the transfers that were made by auto-pairing and can still be split. */
    fun observeAutoPairedIds(): Flow<Set<String>>

    /**
     * Soft-deletes the transfer [transferId], restores the originals it replaced (live again, pair link cleared,
     * `updatedAt` at least one past `createdAt` so they never pair again), puts their `PAIRED_TRANSFER` logs back to
     * `CONFIRMED`, and rebuilds every account of the transfer and the originals, all in one atomic unit.
     */
    suspend fun unpair(
        transferId: String,
        now: Long,
    )
}
