package io.github.toanhp123.finote.domain.transaction.repository

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType

/**
 * Every write keeps `currentBalance` and the monthly snapshots of each touched account in step,
 * all in one atomic unit. A failure leaves nothing behind.
 */
interface TransactionRepository {
    suspend fun getTransaction(id: String): Transaction?

    /** The subset of [accountIds] that exist and are neither soft-deleted nor archived. */
    suspend fun getActiveAccountIds(accountIds: Set<String>): Set<String>

    /** Null when the category does not exist. */
    suspend fun getCategoryType(categoryId: String): CategoryType?

    /** Account of the newest live manual entry, or null when the user has none yet. */
    suspend fun getLastManualAccountId(): String?

    /** Category of the newest live manual entry of [type] that has a category, or null. */
    suspend fun getLastManualCategoryId(type: TransactionType): String?

    suspend fun add(transaction: Transaction)

    suspend fun update(transaction: Transaction)

    suspend fun softDelete(
        id: String,
        deletedAt: Long,
    )
}
