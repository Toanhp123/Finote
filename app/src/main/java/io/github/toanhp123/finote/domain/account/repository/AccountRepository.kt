package io.github.toanhp123.finote.domain.account.repository

import io.github.toanhp123.finote.domain.account.model.Account
import kotlinx.coroutines.flow.Flow

/**
 * Every write is one atomic unit; a failure leaves nothing behind. Business rules are not checked here: they live in
 * `AccountRules`, and the use case runs them before it writes. A write on a missing row does nothing; a soft-deleted
 * row is not checked, so the caller must.
 */
interface AccountRepository {
    /** Includes a soft-deleted account. Null when the id is unknown. */
    suspend fun getAccount(id: String): Account?

    /** Accounts that are not soft-deleted, archived ones included; ordered by `sortOrder`, then `createdAt`. */
    fun observeAccounts(): Flow<List<Account>>

    /** Stores the account with `currentBalance = initialBalance`. */
    suspend fun add(account: Account)

    /**
     * Takes only `name`, `icon`, `color`, `sortOrder`, `isExcludedFromNetWorth` and `updatedAt` from [account]; every
     * other field (type, currency, bank key, mask, balances, `isInitialized`, flags) stays as stored, so an edit
     * cannot break notification matching or the balance.
     *
     * Contract for `name`: pass the stored `name` (the English fallback) unchanged unless the user edited the name
     * field. A different `name` clears `nameKey`. Passing the translated string the UI shows is a different `name`,
     * so it clears `nameKey` and the account loses its localization.
     */
    suspend fun update(account: Account)

    /** Flips `isArchived` only. */
    suspend fun setArchived(
        id: String,
        archived: Boolean,
        updatedAt: Long,
    )

    /** Soft delete. Deleting twice does nothing. */
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )

    /**
     * Folds [sourceId] into [targetId], two rows of one bank account. One write transaction. The caller has already
     * checked `AccountRules.checkMerge`.
     */
    suspend fun merge(
        sourceId: String,
        targetId: String,
        now: Long,
    )

    /** Live transactions where the account is source or transfer destination. */
    suspend fun getLiveTransactionCount(id: String): Int

    /** Transfers between the two accounts in either direction, deleted ones included. */
    suspend fun countTransfersBetween(
        sourceId: String,
        targetId: String,
    ): Int

    /**
     * Sets `isIgnored`. When [ignored] and [shouldTrashOldTransactions], one write transaction also soft-deletes the
     * live non-transfer rows with `accountId = id` and rebuilds its balance.
     */
    suspend fun setIgnored(
        id: String,
        ignored: Boolean,
        shouldTrashOldTransactions: Boolean,
        now: Long,
    )

    /** Live non-transfer rows with `accountId = id`: what [setIgnored] would trash. */
    suspend fun getTrashableTransactionCount(id: String): Int
}
