package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository

/** In-memory stand-in. Accounts in [activeAccounts] count as existing and live. */
class FakeTransactionRepository(
    val activeAccounts: Set<String> = emptySet(),
    val categories: Map<String, CategoryType> = emptyMap(),
) : TransactionRepository {
    val stored = mutableMapOf<String, Transaction>()
    var writes = 0
        private set

    override suspend fun getTransaction(id: String): Transaction? = stored[id]

    override suspend fun getActiveAccountIds(accountIds: Set<String>): Set<String> =
        accountIds.intersect(activeAccounts)

    override suspend fun getCategoryType(categoryId: String): CategoryType? = categories[categoryId]

    override suspend fun add(transaction: Transaction) {
        writes++
        stored[transaction.id] = transaction
    }

    override suspend fun update(transaction: Transaction) {
        writes++
        stored[transaction.id] = transaction
    }

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt)
    }
}
