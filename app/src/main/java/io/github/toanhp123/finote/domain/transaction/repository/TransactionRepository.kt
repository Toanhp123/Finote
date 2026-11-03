package io.github.toanhp123.finote.domain.transaction.repository

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction

/**
 * Every write keeps `currentBalance` and the monthly snapshots of each touched account in step,
 * all in one atomic unit. A failure leaves nothing behind.
 */
interface TransactionRepository {
    suspend fun getTransaction(id: String): Transaction?

    /** The subset of [accountIds] that exist and are not soft-deleted. */
    suspend fun getActiveAccountIds(accountIds: Set<String>): Set<String>

    /** Null when the category does not exist. */
    suspend fun getCategoryType(categoryId: String): CategoryType?

    suspend fun add(transaction: Transaction)

    suspend fun update(transaction: Transaction)

    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
