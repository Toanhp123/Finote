package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Copies from the real repository: `observeCategories` hides deleted rows and sorts by sortOrder, createdAt.
 * Does not copy: business `require` checks (R4/R5 of a later plan move them into the domain).
 */
class FakeCategoryRepository : CategoryRepository {
    val stored = mutableMapOf<String, Category>()
    private val version = MutableStateFlow(0)
    var writes = 0
        private set

    /** Makes [observeCategories] emit again after a test edits [stored] directly. */
    fun notifyChanged() {
        version.value++
    }

    override suspend fun getCategory(id: String): Category? = stored[id]

    override fun observeCategories(): Flow<List<Category>> =
        version.map { _ ->
            stored.values
                .filter { category -> category.deletedAt == null }
                .sortedWith(compareBy<Category> { it.sortOrder }.thenBy { it.createdAt })
        }

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

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt, updatedAt = deletedAt)
        notifyChanged()
    }
}
