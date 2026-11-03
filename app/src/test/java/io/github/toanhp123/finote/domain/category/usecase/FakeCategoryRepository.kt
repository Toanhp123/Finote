package io.github.toanhp123.finote.domain.category.usecase

import io.github.toanhp123.finote.domain.category.model.Category
import io.github.toanhp123.finote.domain.category.repository.CategoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** In-memory stand-in. It does no business checks: those belong to the real repository. */
class FakeCategoryRepository : CategoryRepository {
    val stored = mutableMapOf<String, Category>()
    var writes = 0
        private set

    override suspend fun getCategory(id: String): Category? = stored[id]

    override fun observeCategories(): Flow<List<Category>> = flowOf(stored.values.filter { it.deletedAt == null })

    override suspend fun add(category: Category) {
        writes++
        stored[category.id] = category
    }

    override suspend fun update(category: Category) {
        writes++
        stored[category.id] = category
    }

    override suspend fun setHidden(
        id: String,
        hidden: Boolean,
        updatedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(isHidden = hidden, updatedAt = updatedAt)
    }

    override suspend fun softDelete(
        id: String,
        deletedAt: Long,
    ) {
        writes++
        stored[id] = checkNotNull(stored[id]).copy(deletedAt = deletedAt, updatedAt = deletedAt)
    }
}
