package io.github.toanhp123.finote.domain.transaction.repository

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import kotlinx.coroutines.flow.Flow

/**
 * Every write keeps `currentBalance` and the monthly snapshots of each touched account in step,
 * all in one atomic unit. A failure leaves nothing behind.
 */
interface TransactionRepository {
    suspend fun getTransaction(id: String): Transaction?

    /** The subset of [accountIds] that exist and are usable: not soft-deleted, archived or ignored. */
    suspend fun getUsableAccountIds(accountIds: Set<String>): Set<String>

    /** Null when the category does not exist. */
    suspend fun getCategoryType(categoryId: String): CategoryType?

    /** Account of the newest live manual entry, or null when the user has none yet. */
    suspend fun getLastManualAccountId(): String?

    /** Category of the newest live manual entry of [type] that has a category, or null. */
    suspend fun getLastManualCategoryId(type: TransactionType): String?

    /**
     * Live transactions, newest first. [accountId] null means every account;
     * otherwise the ones that leave it or arrive in it as a transfer.
     */
    fun observeTransactions(accountId: String?): Flow<List<Transaction>>

    suspend fun add(transaction: Transaction)

    /**
     * Writes one ADJUSTMENT that brings the account's currentBalance to [actualBalance], all in one
     * atomic unit. Returns null and writes nothing when the balance already matches.
     */
    suspend fun reconcile(
        id: String,
        accountId: String,
        actualBalance: Long,
        now: Long,
    ): Transaction?

    /**
     * Writes one ADJUSTMENT that settles the account's current balance drift (see `detectBalanceDrift`), all in one
     * atomic unit. The drift is recomputed from stored rows inside the write, so a booking that landed after the
     * caller saw the drift cannot skew the amount. Returns null and writes nothing when there is no drift.
     */
    suspend fun adjustDrift(
        id: String,
        accountId: String,
        now: Long,
    ): Transaction?

    suspend fun update(transaction: Transaction)

    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
