package io.github.toanhp123.finote.domain.account.repository

import io.github.toanhp123.finote.domain.account.model.Account
import io.github.toanhp123.finote.domain.account.model.AccountIdentifier
import io.github.toanhp123.finote.domain.account.model.IdentifierKind
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

    /** Flips `isArchived` only; archiving also clears `isPrimary`. */
    suspend fun setArchived(
        id: String,
        archived: Boolean,
        updatedAt: Long,
    )

    /** Soft delete; also clears `isPrimary`. Deleting twice does nothing. */
    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )

    /** Live transactions where the account is source or transfer destination. */
    suspend fun getLiveTransactionCount(id: String): Int

    /**
     * Sets `isIgnored`; ignoring also clears `isPrimary`. When [ignored] and [shouldTrashOldTransactions], one write
     * transaction also soft-deletes the live non-transfer rows with `accountId = id` and rebuilds its balance.
     */
    suspend fun setIgnored(
        id: String,
        ignored: Boolean,
        shouldTrashOldTransactions: Boolean,
        now: Long,
    )

    /** Live non-transfer rows with `accountId = id`: what [setIgnored] would trash. */
    suspend fun getTrashableTransactionCount(id: String): Int

    /** Highest stored `sortOrder` plus one (soft-deleted rows count); 0 when there are no rows. */
    suspend fun getNextSortOrder(): Int

    /**
     * Sets `initialBalance` and `isInitialized = true`, then rebuilds `currentBalance` and the monthly snapshots from
     * the live transactions.
     */
    suspend fun setInitialBalance(
        id: String,
        initialBalance: Long,
        now: Long,
    )

    /** Clears `isPrimary` on every other row and sets it on [id]. */
    suspend fun setPrimary(
        id: String,
        now: Long,
    )

    suspend fun clearPrimary(
        id: String,
        now: Long,
    )

    /** `sortOrder = index` for each id in [orderedIds]; ids not listed keep their value. */
    suspend fun reorder(
        orderedIds: List<String>,
        now: Long,
    )

    /** Writes [packageName] only while the stored `bankPackageName` is null. */
    suspend fun setBankPackage(
        id: String,
        packageName: String,
        now: Long,
    )

    fun observeIdentifiers(accountId: String): Flow<List<AccountIdentifier>>

    /** Ids of the accounts that own at least one identifier. */
    fun observeIdentifiedAccountIds(): Flow<Set<String>>

    suspend fun getIdentifier(
        packageName: String,
        kind: IdentifierKind,
        mask: String,
    ): AccountIdentifier?

    /**
     * Inserts the key. A key whose owner is soft-deleted moves to the new account, as settlement does. A key owned by
     * a live account throws `StorageException`: the use case checks first.
     */
    suspend fun addIdentifier(identifier: AccountIdentifier)

    /** Hard-deletes that one key, even when its owner is missing or deleted; an unknown key does nothing. */
    suspend fun removeIdentifier(identifier: AccountIdentifier)
}
