package io.github.toanhp123.finote.domain.transaction.usecase

import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.transaction.model.Transaction
import io.github.toanhp123.finote.domain.transaction.model.TransactionType
import io.github.toanhp123.finote.domain.transaction.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** In-memory stand-in. Accounts in [activeAccounts] count as existing and live. */
class FakeTransactionRepository(
    val activeAccounts: Set<String> = emptySet(),
    val categories: Map<String, CategoryType> = emptyMap(),
) : TransactionRepository {
    val stored = mutableMapOf<String, Transaction>()
    var lastAccountId: String? = null
    val lastCategoryIds: MutableMap<TransactionType, String?> = mutableMapOf()
    var writes = 0
        private set
    val reconcileCalls = mutableListOf<ReconcileCall>()
    var reconcileResult: Transaction? = null

    /** Source of [observeTransactions]; a non-null account id keeps rows with that source or transfer destination. */
    val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())
    val observedAccountIds = mutableListOf<String?>()
    var failObserve: Throwable? = null

    data class ReconcileCall(
        val id: String,
        val accountId: String,
        val actualBalance: Long,
        val now: Long,
    )

    override suspend fun getTransaction(id: String): Transaction? = stored[id]

    override suspend fun getActiveAccountIds(accountIds: Set<String>): Set<String> =
        accountIds.intersect(activeAccounts)

    override suspend fun getCategoryType(categoryId: String): CategoryType? = categories[categoryId]

    override suspend fun getLastManualAccountId(): String? = lastAccountId

    override suspend fun getLastManualCategoryId(type: TransactionType): String? = lastCategoryIds[type]

    override fun observeTransactions(accountId: String?): Flow<List<Transaction>> {
        observedAccountIds += accountId
        val failure = failObserve
        return if (failure != null) {
            flow { throw failure }
        } else {
            transactionsFlow.map { rows ->
                rows.filter { accountId == null || it.accountId == accountId || it.toAccountId == accountId }
            }
        }
    }

    override suspend fun add(transaction: Transaction) {
        writes++
        stored[transaction.id] = transaction
    }

    override suspend fun reconcile(
        id: String,
        accountId: String,
        actualBalance: Long,
        now: Long,
    ): Transaction? {
        reconcileCalls += ReconcileCall(id, accountId, actualBalance, now)
        return reconcileResult
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
