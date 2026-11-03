package io.github.toanhp123.finote.domain.account.repository

import io.github.toanhp123.finote.domain.account.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Every write is one atomic unit. A change to `initialBalance` also rebuilds `currentBalance` and the
 * monthly snapshots of the account; a failure leaves nothing behind. Business errors are [IllegalArgumentException].
 */
interface AccountRepository {
    /** Includes a soft-deleted account. Null when the id is unknown. */
    suspend fun getAccount(id: String): Account?

    /** Accounts that are not soft-deleted, archived ones included. */
    fun observeAccounts(): Flow<List<Account>>

    /** Stores the account with `currentBalance = initialBalance`. */
    suspend fun add(account: Account)

    /** Keeps `currentBalance`, `createdAt` and `deletedAt` of the stored row. Missing or deleted is rejected. */
    suspend fun update(account: Account)

    /** Flips `isArchived` only. A missing or deleted account is rejected. */
    suspend fun setArchived(
        id: String,
        archived: Boolean,
        updatedAt: Long,
    )

    /** Soft delete. Rejected while a live transaction still touches the account; deleting twice does nothing. */
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
