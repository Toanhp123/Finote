package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.model.CategoryType
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Copies from the real repository: `observeCategories` hides deleted rows and sorts by sortOrder, createdAt, id.
 * Does not copy: field preservation on write. Business rules live in `CategoryRules`, not in the repository.
 */
class FakeCategoryRepository : CategoryRepository {
    val stored = mutableMapOf<String, Category>()

    /** Transaction id to category id; stands in for the transactions table so tests can count and check moves. */
    val transactionCategory = mutableMapOf<String, String?>()

    /** Ids of [transactionCategory] that are soft-deleted: [countLiveTransactions] skips them. */
    val deletedTransactions = mutableSetOf<String>()

    /** Rule id to category id; stands in for the user keyword rules. Does not copy the duplicate-keyword drop. */
    val ruleCategory = mutableMapOf<String, String>()

    /** Budget id to category id; stands in for the budgets table. Only counted: moves are tested on the real DAO. */
    val budgetCategory = mutableMapOf<String, String>()
    private val version = MutableStateFlow(0)
    var writes = 0
        private set

    /** Makes the next [observeCategories] emission fail, as a Room error in a running query would. */
    var failObserve: Throwable? = null

    /** Makes [observeCategories] emit again after a test edits [stored] directly. */
    fun notifyChanged() {
        version.value++
    }

    override suspend fun getCategory(id: String): Category? = stored[id]

    override fun observeCategories(): Flow<List<Category>> =
        version.map { _ ->
            failObserve?.let { throw it }
            stored.values
                .filter { category -> category.deletedAt == null }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })
        }

    override suspend fun countLiveChildren(id: String): Int =
        stored.values.count { it.parentId == id && it.deletedAt == null }

    override suspend fun add(category: Category) {
        writes++
        stored[category.id] = category
        notifyChanged()
    }

    override suspend fun update(category: Category) {
        writes++
        stored[category.id] = category
        notifyChanged()
    }

    override suspend fun setHidden(
        id: String,
        hidden: Boolean,
        updatedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(isHidden = hidden, updatedAt = updatedAt)
        notifyChanged()
    }

    override suspend fun getLevel(
        type: CategoryType,
        parentId: String?,
    ): List<Category> =
        stored.values
            .filter { it.type == type && it.parentId == parentId && it.deletedAt == null }
            .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.createdAt }.thenBy { it.id })

    override suspend fun countLiveTransactions(categoryIds: Set<String>): Int =
        transactionCategory.count { (id, categoryId) -> id !in deletedTransactions && categoryId in categoryIds }

    override suspend fun countRules(categoryIds: Set<String>): Int = ruleCategory.values.count { it in categoryIds }

    override suspend fun countBudgets(categoryIds: Set<String>): Int = budgetCategory.values.count { it in categoryIds }

    override suspend fun deleteAndMove(
        categoryIds: Set<String>,
        targetId: String?,
        deletedAt: Long,
    ) {
        writes++
        transactionCategory.replaceAll { _, categoryId -> if (categoryId in categoryIds) targetId else categoryId }
        if (targetId == null) {
            ruleCategory.values.removeAll { it in categoryIds }
        } else {
            ruleCategory.replaceAll { _, categoryId -> if (categoryId in categoryIds) targetId else categoryId }
        }
        categoryIds.forEach { id ->
            val old = stored[id]
            if (old != null &&
                old.deletedAt == null
            ) {
                stored[id] = old.copy(deletedAt = deletedAt, updatedAt = deletedAt)
            }
        }
        notifyChanged()
    }

    override suspend fun reorder(
        orderedIds: List<String>,
        now: Long,
    ) {
        writes++
        orderedIds.forEachIndexed { index, id ->
            stored[id]?.let { stored[id] = it.copy(sortOrder = index, updatedAt = now) }
        }
        notifyChanged()
    }
}
